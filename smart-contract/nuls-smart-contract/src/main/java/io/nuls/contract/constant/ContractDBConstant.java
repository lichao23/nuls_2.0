/*
 * MIT License
 *
 * Copyright (c) 2017-2018 nuls.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package io.nuls.contract.constant;

/**
 * 交易数据存储常量
 * Transaction entity storage constants
 *
 * @author: qinyifeng
 */
public interface ContractDBConstant {

    /**
     * 系统语言表名 一个节点共用，不区分chain
     * system language table name
     */
    String DB_NAME_LANGUAGE = "contract_language";
    /**
     * 配置信息表名
     * chain configuration table name
     */
    String DB_NAME_CONGIF = "contract_config";

    String DB_NAME_CONTRACT_LEDGER_TX_INDEX = "contract_ledger_tx_index";
    String DB_NAME_CONTRACT_ADDRESS = "contract_address";
    String DB_NAME_CONTRACT_TRANSFER_TX = "contract_transfer_tx";
    String DB_NAME_CONTRACT_EXECUTE_RESULT = "contract_execute_result";
    String DB_NAME_CONTRACT_COLLECTION = "contract_collection";

    String DB_NAME_CONTRACT_NRC20_TOKEN_TRANSFER = "contract_nrc20_token_transfer";
    String DB_NAME_CONTRACT_NRC20_TOKEN_ADDRESS = "contract_nrc20_token_address";

}