package io.nuls.rpc.util;

import io.nuls.rpc.info.Constants;
import io.nuls.rpc.model.ModuleE;
import io.nuls.rpc.model.message.Response;
import io.nuls.rpc.netty.channel.manager.ConnectManager;
import io.nuls.rpc.netty.processor.ResponseMessageProcessor;
import io.nuls.tools.log.Log;
import io.nuls.tools.protocol.Protocol;
import io.nuls.tools.protocol.TxDefine;
import io.nuls.tools.protocol.TxRegisterDetail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TxRegisterHelper {

    /**
     * 向交易模块注册交易
     * Register transactions with the transaction module
     */
    public static boolean registerTx(int chainId, Protocol protocol) {
        try {
            List<TxRegisterDetail> txRegisterDetailList = new ArrayList<>();
            List<TxDefine> allowTxs = protocol.getAllowTx();
            for (TxDefine config : allowTxs) {
                TxRegisterDetail detail = new TxRegisterDetail();
                detail.setValidator(config.getValidate());
                detail.setCommit(config.getCommit());
                detail.setRollback(config.getRollback());
                detail.setSystemTx(config.isSystemTx());
                detail.setTxType(config.getType());
                detail.setUnlockTx(config.isUnlockTx());
                detail.setVerifySignature(config.isVerifySignature());
                txRegisterDetailList.add(detail);
            }

            //向交易管理模块注册交易
            Map<String, Object> params = new HashMap<>();
            params.put(Constants.VERSION_KEY_STR, "1.0");
            params.put("chainId", chainId);
            params.put("moduleCode", ConnectManager.LOCAL.getAbbreviation());
            params.put("moduleValidator", protocol.getModuleValidator());
            params.put("moduleCommit", protocol.getModuleCommit());
            params.put("moduleRollback", protocol.getModuleRollback());
            params.put("list", txRegisterDetailList);
            Response cmdResp = ResponseMessageProcessor.requestAndResponse(ModuleE.TX.abbr, "tx_register", params);
            if (!cmdResp.isSuccess()) {
                Log.error("chain ：" + chainId + " Failure of transaction registration,errorMsg: " + cmdResp.getResponseComment());
                return false;
            }
        } catch (Exception e) {
            Log.error("", e);
        }
        return true;
    }

}
