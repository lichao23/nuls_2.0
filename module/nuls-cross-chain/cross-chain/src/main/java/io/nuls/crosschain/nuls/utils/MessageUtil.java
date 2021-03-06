package io.nuls.crosschain.nuls.utils;

import io.nuls.base.RPCUtil;
import io.nuls.base.basic.AddressTool;
import io.nuls.base.data.NulsHash;
import io.nuls.base.data.Transaction;
import io.nuls.base.signture.P2PHKSignature;
import io.nuls.base.signture.SignatureUtil;
import io.nuls.base.signture.TransactionSignature;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.crypto.HexUtil;
import io.nuls.core.exception.NulsException;
import io.nuls.core.model.StringUtils;
import io.nuls.crosschain.base.constant.CommandConstant;
import io.nuls.crosschain.base.message.BroadCtxSignMessage;
import io.nuls.crosschain.base.message.GetCtxMessage;
import io.nuls.crosschain.base.message.GetOtherCtxMessage;
import io.nuls.crosschain.base.message.VerifyCtxMessage;
import io.nuls.crosschain.base.model.bo.ChainInfo;
import io.nuls.crosschain.nuls.constant.NulsCrossChainConfig;
import io.nuls.crosschain.nuls.constant.NulsCrossChainConstant;
import io.nuls.crosschain.nuls.model.bo.Chain;
import io.nuls.crosschain.nuls.model.bo.NodeType;
import io.nuls.crosschain.nuls.rpc.call.AccountCall;
import io.nuls.crosschain.nuls.rpc.call.ConsensusCall;
import io.nuls.crosschain.nuls.rpc.call.NetWorkCall;
import io.nuls.crosschain.nuls.rpc.call.TransactionCall;
import io.nuls.crosschain.nuls.srorage.ConvertToCtxService;
import io.nuls.crosschain.nuls.srorage.NewCtxService;
import io.nuls.crosschain.nuls.utils.manager.ChainManager;

import java.io.IOException;
import java.util.*;

/**
 * 消息工具类
 * Message Tool Class
 *
 * @author tag
 * 2019/5/20
 */
@Component
public class MessageUtil {
    @Autowired
    private static NewCtxService newCtxService;

    @Autowired
    private static ConvertToCtxService convertToCtxService;

    @Autowired
    private static NulsCrossChainConfig config;

    @Autowired
    private static ChainManager chainManager;

    /**
     * 对新收到的交易进行处理
     * @param chain     本链信息
     * @param hash      交易Hash
     * @param chainId   发送链ID
     * @param nodeId    发送节点ID
     * @param hashHex   交易Hash字符串（用于日志打印）
     */
    public static void handleNewHash(Chain chain, NulsHash hash, int chainId, String nodeId,String hashHex) {
        int tryCount = 0;
        while (chain.getCtxStageMap().get(hash) == NulsCrossChainConstant.CTX_STAGE_WAIT_RECEIVE && tryCount < NulsCrossChainConstant.BYZANTINE_TRY_COUNT) {
            try {
                Thread.sleep(2000);
            } catch (Exception e) {
                chain.getLogger().error(e);
            }
            tryCount++;
        }
        if (chain.getCtxStageMap().get(hash) == NulsCrossChainConstant.CTX_STAGE_WAIT_RECEIVE) {
            GetOtherCtxMessage responseMessage = new GetOtherCtxMessage();
            responseMessage.setRequestHash(hash);
            NetWorkCall.sendToNode(chainId, responseMessage, nodeId, CommandConstant.GET_OTHER_CTX_MESSAGE);
            chain.getLogger().info("向节点{}获取完整跨链交易，Hash:{}", nodeId, hashHex);
        } else {
            chain.getHashNodeIdMap().putIfAbsent(hash, new ArrayList<>());
            chain.getHashNodeIdMap().get(hash).add(new NodeType(nodeId, 2));
        }
        chain.getLogger().info("节点{}广播过来的跨链交易Hash或签名消息处理完成,Hash：{}\n\n", nodeId, hashHex);
    }

    /**
     * 交易签名拜占庭处理
     * @param chain        本链信息
     * @param chainId      发送链ID
     * @param realHash     本链协议跨链交易Hash
     * @param ctx          跨链交易
     * @param messageBody  消息
     * @param nativeHex    交易Hash字符串
     * @param signHex      交易签名字符串
     */
    public static void signByzantine(Chain chain, int chainId, NulsHash realHash, Transaction ctx, BroadCtxSignMessage messageBody, String nativeHex, String signHex) throws NulsException, IOException {
        //判断节点是否已经收到并广播过该签名，如果已经广播过则不需要再广播
        int handleChainId = chain.getChainId();
        TransactionSignature signature = new TransactionSignature();
        if (ctx.getTransactionSignature() != null) {
            signature.parse(ctx.getTransactionSignature(), 0);
            for (P2PHKSignature sign : signature.getP2PHKSignatures()) {
                if (Arrays.equals(messageBody.getSignature(), sign.serialize())) {
                    chain.getLogger().info("本节点已经收到过该跨链交易的该签名,Hash:{},签名:{}\n\n", nativeHex, signHex);
                    return;
                }
            }
        } else {
            List<P2PHKSignature> p2PHKSignatureList = new ArrayList<>();
            signature.setP2PHKSignatures(p2PHKSignatureList);
        }
        P2PHKSignature p2PHKSignature = new P2PHKSignature();
        p2PHKSignature.parse(messageBody.getSignature(), 0);
        signature.getP2PHKSignatures().add(p2PHKSignature);
        //交易签名拜占庭
        List<String> packAddressList = CommonUtil.getCurrentPackAddresList(chain);
        int byzantineCount = CommonUtil.getByzantineCount(packAddressList, chain);
        int signCount = signature.getP2PHKSignatures().size();
        if (signCount >= byzantineCount) {
            List<P2PHKSignature> misMatchSignList = CommonUtil.getMisMatchSigns(chain, signature, packAddressList);
            signCount -= misMatchSignList.size();
            if (signCount >= byzantineCount) {
                ctx.setTransactionSignature(signature.serialize());
                newCtxService.save(realHash, ctx, handleChainId);
                TransactionCall.sendTx(chain, RPCUtil.encode(ctx.serialize()));
                chain.getLogger().info("签名拜占庭验证通过,将跨链交易广播给交易模块处理，签名数量为：{}\n\n", signature.getP2PHKSignatures().size());
                return;
            } else {
                signature.getP2PHKSignatures().addAll(misMatchSignList);
                ctx.setTransactionSignature(signature.serialize());
            }
        } else {
            ctx.setTransactionSignature(signature.serialize());
        }
        newCtxService.save(realHash, ctx, handleChainId);
        NetWorkCall.broadcast(chainId, messageBody, CommandConstant.BROAD_CTX_SIGN_MESSAGE, false);
        chain.getLogger().info("将收到的跨链交易签名广播给链接到的其他节点,Hash:{},签名:{}\n\n", nativeHex, signHex);
    }

    /**
     * 处理收到的新交易
     *
     * @param ctx          收到的交易
     * @param originalHash 本地协议对应的原交易hash
     * @param nativeHash   本地交易Hash
     * @param chain        该交易所属链
     * @param fromChainId  跨链链接标志
     * @return 处理是否成功
     */
    public static boolean handleNewCtx(Transaction ctx,NulsHash originalHash, NulsHash nativeHash, Chain chain, int fromChainId, String nativeHex, String originalHex, boolean isLocalCtx) {
        TransactionSignature transactionSignature = new TransactionSignature();
        try {
            //如果是其他链发送过来的跨链交易一定需要验证签名，如果为本链节点发送的跨链交易，如果有签名则需验证签名，如果没有不用验证
            transactionSignature.parse(ctx.getTransactionSignature(), 0);
            if (!isLocalCtx) {
                if (!SignatureUtil.validateTransactionSignture(ctx)) {
                    chain.getLogger().error("Signature verification error");
                    return false;
                }
                ctx.setTransactionSignature(null);
                transactionSignature.setP2PHKSignatures(null);
            } else {
                if (transactionSignature.getP2PHKSignatures() != null && transactionSignature.getP2PHKSignatures().size() > 0) {
                    if (!SignatureUtil.validateTransactionSignture(ctx)) {
                        chain.getLogger().error("Signature verification error");
                        return false;
                    }
                }
            }
        } catch (NulsException e) {
            chain.getLogger().error(e);
            return false;
        }
        VerifyCtxMessage verifyCtxMessage = new VerifyCtxMessage();
        verifyCtxMessage.setOriginalCtxHash(originalHash);
        verifyCtxMessage.setRequestHash(nativeHash);
        NetWorkCall.broadcast(fromChainId, verifyCtxMessage, CommandConstant.VERIFY_CTX_MESSAGE, true);
        chain.getLogger().info("本节点第一次收到该跨链交易，需向连接到的发送链节点验证该跨链交易,originalHash:{},Hash:{}", originalHex, nativeHex);
        if (!chain.getVerifyCtxResultMap().containsKey(nativeHash)) {
            chain.getVerifyCtxResultMap().put(nativeHash, new ArrayList<>());
        }
        //接收验证结果，统计结果并做拜占庭得到最终验证结果，如果验证结果为验证不通过则删除该消息
        boolean validResult = verifyResult(chain, fromChainId, nativeHash);
        //如果验证不通过，结束
        if (!validResult) {
            chain.getLogger().info("该跨链交易拜占庭验证失败，originalHash:{},Hash:{}\n", originalHex, nativeHex);
            return false;
        }
        //如果不是链内协议交易，本链为接收链且不为主链则需要生成本链协议跨链交易
        Transaction localCtx = ctx;
        try {
            if (!isLocalCtx) {
                int toChainId = AddressTool.getChainIdByAddress(ctx.getCoinDataInstance().getTo().get(0).getAddress());
                if (!chain.isMainChain() && toChainId == chain.getChainId()) {
                    localCtx = TxUtil.mainConvertToFriend(ctx, config.getCrossCtxType());
                    nativeHash = localCtx.getHash();
                    nativeHex = nativeHash.toHex();
                    chain.getLogger().info("主网协议跨链交易转换为本链协议完成，本链协议交易Hash为：{}", nativeHex);
                }
            }
        } catch (Exception e) {
            chain.getLogger().error(e);
            return false;
        }

        //处理交易签名
        try {
            if (!signCtx(chain, localCtx, originalHash, nativeHash, nativeHex, originalHex, transactionSignature)) {
                return false;
            }
        } catch (Exception e) {
            chain.getLogger().error("跨链交易签名失败,originalHash:{},Hash:{}", originalHex, nativeHex);
            chain.getLogger().error(e);
            return false;
        }

        //保存跨链交易
        if (!saveNewCtx(localCtx, chain, originalHash, nativeHex, originalHex)) {
            chain.getLogger().info("跨链交易保存失败，originalHash:{},Hash:{}", originalHex, nativeHex);
            return false;
        }
        //广播缓存中的签名
        broadcastCtx(chain, nativeHash, chain.getChainId(), originalHex, nativeHex);
        return true;
    }

    /**
     * 从广播交易hash或签名消息的节点中获取完整跨链交易处理
     * @param chain           本链信息
     * @param chainId         发送链ID
     * @param cacheHash       缓存的交易Hash
     * @param nativeHash      本地协议跨链交易Hash
     * @param originalHash    发送连协议跨链交易Hash
     * @param originalHex     发送链协议交易Hash字符串
     * @param nativeHex       本链协议交易Hash字符串
     *
     * */
    public static void regainCtx(Chain chain, int chainId, NulsHash cacheHash, NulsHash nativeHash, NulsHash originalHash, String originalHex, String nativeHex) {
        NodeType nodeType = chain.getHashNodeIdMap().get(cacheHash).remove(0);
        if (chain.getHashNodeIdMap().get(cacheHash).isEmpty()) {
            chain.getHashNodeIdMap().remove(cacheHash);
        }
        //如果为链内节点，则发送获取链内交易消息，否则获取链外交易消息
        if (nodeType.getNodeType() == 1) {
            GetCtxMessage responseMessage = new GetCtxMessage();
            responseMessage.setRequestHash(nativeHash);
            NetWorkCall.sendToNode(chainId, responseMessage, nodeType.getNodeId(), CommandConstant.GET_CTX_MESSAGE);
            chain.getLogger().info("跨链交易处理失败，向链内节点：{}重新获取跨链交易，originalHash:{},Hash:{}", nodeType.getNodeId(), originalHex, nativeHex);

        } else {
            GetOtherCtxMessage responseMessage = new GetOtherCtxMessage();
            responseMessage.setRequestHash(originalHash);
            NetWorkCall.sendToNode(chainId, responseMessage, nodeType.getNodeId(), CommandConstant.GET_OTHER_CTX_MESSAGE);
            chain.getLogger().info("跨链交易处理失败，向其他链节点：{}重新获取跨链交易，originalHash:{},Hash:{}", nodeType.getNodeId(), originalHex, nativeHex);
        }
    }

    /**
     * 统计交易验证结果
     * @param chain        本链信息
     * @param fromChainId  发送链ID
     * @param requestHash  交易Hash
     * @return  验证是否成功
     */
    private static boolean verifyResult(Chain chain, int fromChainId, NulsHash requestHash) {
        try {
            int linkedNode = NetWorkCall.getAvailableNodeAmount(fromChainId, true);
            int verifySuccessCount = linkedNode * chain.getConfig().getByzantineRatio() / NulsCrossChainConstant.MAGIC_NUM_100;
            chain.getLogger().info("当前链接到的跨链节点数为：{}，拜占庭比例为:{},最少需要验证通过数量:{}", linkedNode, chain.getConfig().getByzantineRatio(), verifySuccessCount);
            int tryCount = 0;
            boolean validResult = false;
            while (tryCount < NulsCrossChainConstant.BYZANTINE_TRY_COUNT) {
                if (chain.getVerifyCtxResultMap().get(requestHash).size() < verifySuccessCount) {
                    Thread.sleep(2000);
                    tryCount++;
                    continue;
                }
                validResult = chain.verifyResult(requestHash, verifySuccessCount);
                if (validResult || chain.getVerifyCtxResultMap().get(requestHash).size() >= linkedNode) {
                    break;
                }
                Thread.sleep(2000);
                tryCount++;
            }
            chain.getLogger().info("跨链交易拜占庭验证完成，验证结果为：{}", validResult);
            return validResult;
        } catch (Exception e) {
            chain.getLogger().error(e);
            return false;
        } finally {
            chain.getVerifyCtxResultMap().remove(requestHash);
        }
    }

    /**
     * 处理接收到的新交易
     *
     * @param ctx            本链协议跨链交易
     * @param chain          本链信息
     * @param originalHash   发送连协议跨链交易Hash
     * @param nativeHex      Hash字符串
     * @param originalHex    Hash字符串
     * @return               保存是否成功
     */
    private static boolean saveNewCtx(Transaction ctx, Chain chain, NulsHash originalHash, String nativeHex, String originalHex) {
        int handleChainId = chain.getChainId();
        /*
         * 主网中传输的都是主网协议的跨链交易所以不用做处理，如果是友链接收到主网发送来的跨链主网协议跨链交易需要生成对应的本链协议跨链交易
         * 如果友链收到本链节点广播的跨链交易，需要找到该交易对应的主网协议跨链交易的Hash（txData中保存）然后保存
         * */
        if (convertToCtxService.save(originalHash, ctx.getHash(), handleChainId)) {
            if (!newCtxService.save(ctx.getHash(), ctx, handleChainId)) {
                convertToCtxService.delete(originalHash, handleChainId);
                chain.getLogger().error("新跨链交易保存失败,originalHash:{},localHash:{}", originalHex, nativeHex);
                return false;
            }
        } else {
            return false;
        }
        chain.getLogger().info("新跨链交易保存成功,originalHash:{},localHash:{}", originalHex, nativeHex);
        return true;
    }

    /**
     * 验证完成的跨链交易签名并广播给链内其他节点
     * @param chain                本链信息
     * @param ctx                  本链协议跨链交易
     * @param originalHash         发送连协议跨链交易Hash
     * @param nativeHash           本链协议跨链交易Hash
     * @param nativeHex            本链协议跨链交易Hash字符串
     * @param originalHex          发送连协议跨链交易Hash字符串
     * @param transactionSignature 本链协议跨链交易签名
     * @return                     签名处理是否成功
     */
    @SuppressWarnings("unchecked")
    private static boolean signCtx(Chain chain, Transaction ctx, NulsHash originalHash, NulsHash nativeHash, String nativeHex, String originalHex, TransactionSignature transactionSignature) throws NulsException, IOException {
        /*
         * 如果本地缓存有该跨链交易未广播的签名，需要把签名加入到交易的签名列表中
         * */
        if (transactionSignature.getP2PHKSignatures() == null) {
            transactionSignature.setP2PHKSignatures(new ArrayList<>());
        }
        if (chain.getWaitBroadSignMap().keySet().contains(nativeHash)) {
            for (BroadCtxSignMessage message : chain.getWaitBroadSignMap().get(nativeHash)) {
                for (P2PHKSignature sign : transactionSignature.getP2PHKSignatures()) {
                    if (Arrays.equals(message.getSignature(), sign.serialize())) {
                        break;
                    }
                }
                P2PHKSignature p2PHKSignature = new P2PHKSignature();
                p2PHKSignature.parse(message.getSignature(), 0);
                transactionSignature.getP2PHKSignatures().add(p2PHKSignature);
            }
        }
        BroadCtxSignMessage message = new BroadCtxSignMessage();
        message.setRequestHash(nativeHash);
        message.setOriginalHash(originalHash);
        //判断本节点是否为共识节点，如果为共识节点则对该交易签名
        Map packerInfo = ConsensusCall.getPackerInfo(chain);
        List<String> packAddressList = (List<String>) packerInfo.get("packAddressList");
        int agentCount = packAddressList.size();
        int signCount = transactionSignature.getP2PHKSignatures().size();
        int minPassCount = agentCount * chain.getConfig().getByzantineRatio() / NulsCrossChainConstant.MAGIC_NUM_100;
        if (minPassCount == 0) {
            minPassCount = 1;
        }
        boolean isDuplicate = false;
        //判断交易签名与当前轮次共识节点出块账户是否匹配
        List<P2PHKSignature> misMatchSignList = null;
        if (signCount >= minPassCount) {
            misMatchSignList = CommonUtil.getMisMatchSigns(chain, transactionSignature, packAddressList);
            signCount -= misMatchSignList.size();
            isDuplicate = true;
            if (signCount >= minPassCount) {
                ctx.setTransactionSignature(transactionSignature.serialize());
                TransactionCall.sendTx(chain, RPCUtil.encode(ctx.serialize()));
                chain.getLogger().info("跨链交易签名数量达到拜占庭比例，将该跨链交易发送给交易模块处理,originalHash:{},localHash:{}", originalHex, nativeHex);
                return true;
            }
        }
        String password = (String) packerInfo.get("password");
        String address = (String) packerInfo.get("address");
        if (!StringUtils.isBlank(address)) {
            chain.getLogger().info("本节点为共识节点，对跨链交易签名,originalHash:{},localHash:{}", originalHex, nativeHex);
            P2PHKSignature p2PHKSignature = AccountCall.signDigest(address, password, ctx.getHash().getBytes());
            message.setSignature(p2PHKSignature.serialize());
            signCount++;
            transactionSignature.getP2PHKSignatures().add(p2PHKSignature);
            if (signCount >= minPassCount) {
                if (!isDuplicate) {
                    misMatchSignList = CommonUtil.getMisMatchSigns(chain, transactionSignature, packAddressList);
                    signCount -= misMatchSignList.size();
                }
                if (signCount >= minPassCount) {
                    ctx.setTransactionSignature(transactionSignature.serialize());
                    TransactionCall.sendTx(chain, RPCUtil.encode(ctx.serialize()));
                    chain.getLogger().info("跨链交易签名数量达到拜占庭比例，将该跨链交易发送给交易模块处理,originalHash:{},localHash:{}", originalHex, nativeHex);
                }
            }
            if (misMatchSignList != null && misMatchSignList.size() > 0 && signCount < minPassCount) {
                transactionSignature.getP2PHKSignatures().addAll(misMatchSignList);
                ctx.setTransactionSignature(transactionSignature.serialize());
            }
        }
        //将收到的消息放入缓存中，等到收到交易后再广播该签名给其他节点
        if (!chain.getWaitBroadSignMap().keySet().contains(nativeHash)) {
            chain.getWaitBroadSignMap().put(nativeHash, new HashSet<>());
        }
        chain.getWaitBroadSignMap().get(nativeHash).add(message);
        return true;
    }

    /**
     * 广播签名
     * @param chain         本链信息
     * @param hash          要广播的交易hash
     * @param chainId       接收链ID
     * @param originalHex   发送送协议交易Hash
     * @param nativeHex     本链协议交易Hash
     */
    private static void broadcastCtx(Chain chain, NulsHash hash, int chainId, String originalHex, String nativeHex) {
        if (chain.getWaitBroadSignMap().get(hash) != null) {
            Iterator<BroadCtxSignMessage> iterator = chain.getWaitBroadSignMap().get(hash).iterator();
            while (iterator.hasNext()) {
                BroadCtxSignMessage message = iterator.next();
                if (NetWorkCall.broadcast(chainId, message, CommandConstant.BROAD_CTX_SIGN_MESSAGE, false)) {
                    iterator.remove();
                    String signStr = "";
                    if (message.getSignature() != null) {
                        signStr = HexUtil.encode(message.getSignature());
                    }
                    chain.getLogger().info("将跨链交易签名广播给链内其他节点,originalHash:{},localHash:{},sign:{}", originalHex, nativeHex, signStr);
                }
            }
            if (chain.getWaitBroadSignMap().get(hash).isEmpty()) {
                chain.getWaitBroadSignMap().remove(hash);
            }
        }
    }

    /**
     * 是否可以发送跨链相关消息
     * @param chain      本链信息
     * @param toChainId  接收链ID
     * @return           是否可发送跨链消息
     * */
    public static boolean canSendMessage(Chain chain,int toChainId) {
        try {
            int linkedNode = NetWorkCall.getAvailableNodeAmount(toChainId, true);
            int minNodeAmount = chain.getConfig().getMinNodeAmount();
            if(config.isMainNet()){
                for (ChainInfo chainInfo:chainManager.getRegisteredCrossChainList()) {
                    if(chainInfo.getChainId() == toChainId){
                        minNodeAmount = chainInfo.getMinAvailableNodeNum();
                    }
                }
            }
            if(linkedNode >= minNodeAmount){
                return true;
            } else {
                chain.getLogger().info("当前节点链接到的跨链节点数小于最小链接数,crossChainId:{},linkedNodeCount:{},minLinkedCount:{}", toChainId, linkedNode, minNodeAmount);
            }
        }catch (NulsException e){
            chain.getLogger().error(e);
        }
        return false;
    }
}
