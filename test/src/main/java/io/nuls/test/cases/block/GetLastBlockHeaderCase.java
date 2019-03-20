package io.nuls.test.cases.block;

import io.nuls.api.provider.ServiceManager;
import io.nuls.api.provider.block.BlockService;
import io.nuls.base.data.BlockHeader;
import io.nuls.test.cases.TestCaseIntf;
import io.nuls.test.cases.TestFailException;
import io.nuls.tools.core.annotation.Component;

/**
 * @Author: zhoulijun
 * @Time: 2019-03-20 13:40
 * @Description: 功能描述
 */
@Component
public class GetLastBlockHeaderCase implements TestCaseIntf<BlockHeader,Void> {

    BlockService blockService = ServiceManager.get(BlockService.class);

    @Override
    public String title() {
        return "获取最新区块头";
    }

    @Override
    public BlockHeader doTest(Void param, int depth) throws TestFailException {

        return null;
    }

}