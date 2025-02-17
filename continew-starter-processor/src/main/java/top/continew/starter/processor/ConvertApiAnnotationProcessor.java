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

package top.continew.starter.processor;

import com.sun.source.tree.Tree.Kind;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.Set;

/**
 * 注解处理器实现(编译期执行),实现类型lombok的@Data注解功能
 * SupportedAnnotationTypes用于指定改processor支持的注解
 */
//@AutoService(Processor.class) // 自动注册处理器
@SupportedAnnotationTypes("top.continew.starter.processor.ConvertApi")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class ConvertApiAnnotationProcessor extends AbstractProcessor {

    /**
     * 描述语法树的实例类
     */
    private JavacTrees javacTrees;

    /**
     * 创建语法树节点的工具类
     */
    private TreeMaker treeMaker;

    /**
     * 访问语法树中的标识符
     * eg:names.fromString("str")
     */
    private Names names;

    /**
     * 从AST上下文中初始化JavacTrees,TreeMaker与Names
     */
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        Context context = ((JavacProcessingEnvironment)processingEnv).getContext();

        javacTrees = JavacTrees.instance(processingEnv);

        treeMaker = TreeMaker.instance(context);

        names = Names.instance(context);

    }

    /**
     * 生成getter方法节点
     */
    private JCTree.JCMethodDecl genGetterMethod(JCTree.JCVariableDecl jcVariableDecl) {
        Name variableDeclName = jcVariableDecl.getName();
        //生成语句: return this.xxx
        JCTree.JCReturn returnStatement = treeMaker.Return(treeMaker.Select(treeMaker.Ident(names
            .fromString("this")), variableDeclName));
        ListBuffer<JCTree.JCStatement> statements = new ListBuffer<JCTree.JCStatement>().append(returnStatement);

        //生成public修饰符
        JCTree.JCModifiers modifiers = treeMaker.Modifiers(Flags.PUBLIC);

        //拼接方法名 getXxx
        String getMethodNameStr = "get" + variableDeclName.toString().substring(0, 1).toUpperCase() + variableDeclName
            .toString()
            .substring(1);
        Name getMethodName = names.fromString(getMethodNameStr);

        //生成返回类型标识
        JCTree.JCExpression returnMethodType = jcVariableDecl.vartype;

        //生成方法体
        JCTree.JCBlock body = treeMaker.Block(0, statements.toList());

        //生成泛型参数列表
        com.sun.tools.javac.util.List<JCTree.JCTypeParameter> methodGenericParameterList = com.sun.tools.javac.util.List
            .nil();

        //生成参数值列表
        com.sun.tools.javac.util.List<JCTree.JCVariableDecl> methodParameterValList = com.sun.tools.javac.util.List
            .nil();

        //生成抛出的异常列表
        com.sun.tools.javac.util.List<JCTree.JCExpression> throwExceptionList = com.sun.tools.javac.util.List.nil();

        //生成方法定义AST节点
        return treeMaker.MethodDef(modifiers,  //public
            getMethodName, //方法名: getXxx
            returnMethodType, //返回类型
            methodGenericParameterList, //泛型参数列表
            methodParameterValList, //参数值列表
            throwExceptionList, //抛出异常列表
            body, //方法体
            null);
    }

    /**
     * 生成setter方法节点
     */
    private JCTree.JCMethodDecl genSetterMethod(JCTree.JCVariableDecl jcVariableDecl) {
        Name variableDeclName = jcVariableDecl.getName();
        //生成语句: this.xxx = xxx
        JCTree.JCExpressionStatement statement = treeMaker.Exec(treeMaker.Assign(treeMaker.Select(treeMaker.Ident(names
            .fromString("this")), variableDeclName),//左表达式部分: this.xxx
            treeMaker.Ident(variableDeclName) //右表达式部分: xxx
        ));
        ListBuffer<JCTree.JCStatement> statements = new ListBuffer<JCTree.JCStatement>().append(statement);

        //setter方法参数
        JCTree.JCVariableDecl param = treeMaker.VarDef(treeMaker
            .Modifiers(Flags.PARAMETER, com.sun.tools.javac.util.List.nil()), //访问修饰符
            variableDeclName, //变量名字
            jcVariableDecl.vartype, //变量类型
            null //变量初始值
        );

        //生成public修饰符
        JCTree.JCModifiers modifiers = treeMaker.Modifiers(Flags.PUBLIC);

        //拼接方法名 getXxx
        String getMethodNameStr = "set" + variableDeclName.toString().substring(0, 1).toUpperCase() + variableDeclName
            .toString()
            .substring(1);
        Name getMethodName = names.fromString(getMethodNameStr);

        //生成返回类型标识 void
        JCTree.JCExpression returnMethodType = treeMaker.Type(new Type.JCVoidType());

        //生成方法体
        JCTree.JCBlock body = treeMaker.Block(0, statements.toList());

        //生成泛型参数列表
        com.sun.tools.javac.util.List<JCTree.JCTypeParameter> methodGenericParameterList = com.sun.tools.javac.util.List
            .nil();

        //生成参数值列表
        com.sun.tools.javac.util.List<JCTree.JCVariableDecl> methodParameterList = com.sun.tools.javac.util.List
            .of(param);

        //生成抛出的异常列表
        com.sun.tools.javac.util.List<JCTree.JCExpression> throwExceptionList = com.sun.tools.javac.util.List.nil();

        //生成方法定义AST节点
        return treeMaker.MethodDef(modifiers,  //public
            getMethodName, // 方法名: setXxx
            returnMethodType, //返回类型
            methodGenericParameterList, //泛型参数列表
            methodParameterList, //参数类型列表
            throwExceptionList, //抛出异常列表
            body, //方法体
            null);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        //获取标注了MyData注解的元素, 这里实际上只有类元素
        Set<? extends Element> set = roundEnv.getElementsAnnotatedWith(ConvertApi.class);

        for (Element element : set) {
            //获取标注了MyData注解的类的语法树
            JCTree tree = javacTrees.getTree(element);

            //notice: 解决编译错误【java.lang.AssertionError: Value of x -1】
            // 因为treeMaker.pos的值是不会变的(=-1), 所以在遍历是需要实时更新
            treeMaker.pos = tree.pos;

            //遍历语法树(在遇到visitClassDef事件, 也就是访问到类定义节点时去修改语法树节点)
            tree.accept(new TreeTranslator() {
                @Override
                public void visitClassDef(JCClassDecl jcClassDecl) {
                    //获取定义在该类下的所有元素(成员变量, 方法等)
                    jcClassDecl.defs.stream()
                        //过滤出变量类型的元素
                        .filter(o -> o.getKind().equals(Kind.VARIABLE))
                        //强制转换元素为变量类型元素
                        .map(o -> ((JCTree.JCVariableDecl)o))
                        //遍历处理每个变量元素
                        .forEach(o -> {
                            //为类定义节点新增getter方法
                            jcClassDecl.defs = jcClassDecl.defs.prepend(genGetterMethod(o));

                            //为类定义节点新增setter方法
                            jcClassDecl.defs = jcClassDecl.defs.prepend(genSetterMethod(o));
                        });
                    //修改类节点完毕
                    super.visitClassDef(jcClassDecl);
                }
            });
        }

        return true;
    }
}