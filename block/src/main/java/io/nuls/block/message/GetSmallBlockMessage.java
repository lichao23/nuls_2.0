/*
 * MIT License
 * Copyright (c) 2017-2018 nuls.io
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.nuls.block.message;

import io.nuls.base.basic.NulsByteBuffer;
import io.nuls.base.data.message.BaseMessage;
import io.nuls.block.constant.CommandConstant;
import io.nuls.block.message.body.GetSmallBlockMessageBody;
import io.nuls.tools.exception.NulsException;
import lombok.Data;

/**
 * 请求获取小区块消息
 * @author captain
 * @date 18-11-9 下午2:37
 * @version 1.0
 */
@Data
public class GetSmallBlockMessage extends BaseMessage<GetSmallBlockMessageBody> {

    @Override
    public GetSmallBlockMessageBody parseMessageBody(NulsByteBuffer byteBuffer) throws NulsException {
        try {
            return byteBuffer.readNulsData(new GetSmallBlockMessageBody());
        } catch (Exception e) {
            throw new NulsException(e);
        }
    }

    public GetSmallBlockMessage() {
        super(CommandConstant.GET_SMALL_BLOCK_MESSAGE);
    }

    public GetSmallBlockMessage(long magicNumber, String cmd, GetSmallBlockMessageBody body) {
        super(cmd, magicNumber);
        this.setMsgBody(body);
    }

}