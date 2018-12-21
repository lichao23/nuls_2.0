package io.nuls.transaction.message;

import io.nuls.base.basic.NulsByteBuffer;
import io.nuls.base.basic.NulsOutputStreamBuffer;
import io.nuls.base.data.NulsDigestData;
import io.nuls.tools.exception.NulsException;
import io.nuls.tools.parse.SerializeUtils;
import io.nuls.transaction.message.base.BaseMessage;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;

/**
 * 友链节点发送协议转换后的跨链交易hash，向主网节点验证跨链交易
 *
 * @author: qinyifeng
 * @date: 2018/12/18
 */
public class VerifyCrossWithMainMessage extends BaseMessage {
    /**
     * 链ID
     */
    @Getter
    @Setter
    private int chainId;

    /**
     * 交易hash
     */
    @Getter
    @Setter
    private NulsDigestData requestHash;

    @Override
    public int size() {
        int size = 0;
        size += SerializeUtils.sizeOfUint16();
        size += SerializeUtils.sizeOfNulsData(requestHash);
        return size;
    }

    @Override
    public void serializeToStream(NulsOutputStreamBuffer stream) throws IOException {
        stream.writeUint16(chainId);
        stream.writeNulsData(requestHash);
    }

    @Override
    public void parse(NulsByteBuffer byteBuffer) throws NulsException {
        this.chainId = byteBuffer.readUint16();
        this.requestHash = byteBuffer.readHash();
    }
}