/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 * <p>
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE 3.0;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.gnu.org/licenses/lgpl.html
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package top.continew.starter.processor.utils;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;

import java.util.ArrayList;
import java.util.List;

public class SqlParser {
    public static class ParseResult {
        public List<String> selectFields = new ArrayList<>(); // 查询字段列表
        public List<String> whereParams = new ArrayList<>();  // 条件参数列表
        public String tableName;                              // 主表名
    }

    public static ParseResult parse(String sql) throws JSQLParserException {
        ParseResult result = new ParseResult();
        Select select = (Select)CCJSqlParserUtil.parse(sql);
        PlainSelect plainSelect = (PlainSelect)select.getSelectBody();

        // 解析SELECT字段
        plainSelect.getSelectItems().forEach(item -> {
            if (item instanceof SelectItem) {
                String field = item.getExpression().toString();
                result.selectFields.add(field);
            }
        });

        // 解析WHERE条件参数
        if (plainSelect.getWhere() != null) {
            plainSelect.getWhere().accept(new ExpressionVisitorAdapter() {
                @Override
                public void visit(Column column) {
                    result.whereParams.add(column.getColumnName());
                }
            });
        }

        // 获取主表名
        if (plainSelect.getFromItem() instanceof Table) {
            result.tableName = ((Table)plainSelect.getFromItem()).getName();
        }

        return result;
    }
}