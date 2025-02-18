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
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.util.TablesNamesFinder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SelectSqlParser {
    private String sql;
    private Select statement;
    private PlainSelect plain;

    public SelectSqlParser(String sql) {
        this.sql = removePlaceholders(sql);
        try {
            Statement statementTemp = CCJSqlParserUtil.parse(sql);
            if (!(statementTemp instanceof Select)) {
                throw new RuntimeException("不是select语句");
            }
            statement = (Select)statementTemp;
            plain = (PlainSelect)statement.getSelectBody();
        } catch (JSQLParserException e) {
            e.printStackTrace();
        }
    }

    public SelectSqlParser parse(String sql) {
        return new SelectSqlParser(sql);
    }

    public Set<String> getTables() {
        try {
            return TablesNamesFinder.findTables(this.sql);
        } catch (JSQLParserException e) {
            throw new RuntimeException(e);
        }
    }

    public Set<String> getFiled() {
        Set<String> filedSet = new HashSet<>();

        // 获取 select
        List<SelectItem<?>> selectItems = plain.getSelectItems();
        for (SelectItem<?> selectItem : selectItems) {
            if (selectItem.getAlias() != null) {
                filedSet.add(selectItem.getAlias().getName());
            } else {
                filedSet.add(selectItem.getExpression().toString());
            }
        }
        return filedSet;
    }

    public Set<String> getGroupBy() {
        // 获取group by
        ExpressionList GroupByColumnReferences = plain.getGroupBy().getGroupByExpressionList();
        Set<String> groupBys = new HashSet<>();
        if (GroupByColumnReferences != null) {
            for (int i = 0; i < GroupByColumnReferences.size(); i++) {
                groupBys.add(GroupByColumnReferences.get(i).toString());
            }
        }
        return groupBys;
    }

    public Set<String> getOrderBy() {
        // 获取order by
        List<OrderByElement> OrderByElements = plain.getOrderByElements();
        Set<String> orderBys = new HashSet<>();
        if (OrderByElements != null) {
            for (int i = 0; i < OrderByElements.size(); i++) {
                orderBys.add(OrderByElements.get(i).toString());
            }
        }
        return new HashSet<>(orderBys);
    }

    public List<String> getWhere() {
        // 获取 where条件
        Expression where_expression = plain.getWhere();
        List<String> where = new ArrayList<>();
        where_expression.accept(new ExpressionVisitorAdapter() {
            @Override
            public void visit(Column column) {
                where.add(column.getColumnName());
            }
        });
        return where;
    }

    // 获取sql语句中的变量
    public static List<String> extractPlaceholders(String sql) {
        List<String> result = new ArrayList<>();
        Pattern pattern = Pattern.compile("#\\{(\\w+)\\}");
        Matcher matcher = pattern.matcher(sql);
        while (matcher.find()) {
            result.add(matcher.group(1)); // 获取括号内的变量名
        }
        return result;
    }

    // 移除sql语句中的占位符
    public static String removePlaceholders(String sql) {
        return sql.replaceAll("#\\{(\\w+)\\}", "$1");
    }
}
