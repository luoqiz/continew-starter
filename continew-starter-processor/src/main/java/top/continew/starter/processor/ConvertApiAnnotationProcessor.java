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

import com.google.common.base.CaseFormat;
import com.squareup.javapoet.*;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import top.continew.starter.processor.utils.SelectSqlParser;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.Elements;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 注解处理器实现(编译期执行),实现类型lombok的@Data注解功能
 * SupportedAnnotationTypes用于指定改processor支持的注解
 */

//@AutoService(Processor.class) // 自动注册处理器
@SupportedAnnotationTypes("top.continew.starter.processor.ConvertApi")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class ConvertApiAnnotationProcessor extends AbstractProcessor {
    // 获取代码生成工具
    private Elements elementUtils;
    private Filer filer;
    private Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        elementUtils = env.getElementUtils();
        filer = env.getFiler();
        messager = env.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(ConvertApi.class)) {
            if (element.getKind() == ElementKind.CLASS) {
                try {
                    processClass((TypeElement)element);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return true;
    }

    private void processClass(TypeElement classElement) throws IOException {
        // 解析注解参数
        ConvertApi convertApi = classElement.getAnnotation(ConvertApi.class);
        // 生成实体类、Mapper、Service、Controller
        generateEntity(classElement, convertApi);
        generateRequestEntity(classElement, convertApi);

        // 生成Mapper接口
        generateMapper(classElement, convertApi);
        // 生成XML文件
        generateXml(classElement, convertApi);
        generateService(classElement, convertApi);
        generateServiceImpl(classElement, convertApi);
        generateController(classElement, convertApi);
    }

    private void generateEntity(TypeElement classElement, ConvertApi convertApi) {
        try {
            // 获取目标实体类的字段
            List<VariableElement> entityFields = classElement.getEnclosedElements()
                .stream()
                .filter(e -> e.getKind() == ElementKind.FIELD)
                .map(e -> (VariableElement)e)
                .collect(Collectors.toList());
            List<FieldSpec> fields = new ArrayList<>();
            // 构造实体类的字段
            for (VariableElement fieldElement : entityFields) {
                FieldSpec fieldSpec = FieldSpec.builder(TypeName.get(fieldElement.asType()), fieldElement
                    .getSimpleName()
                    .toString(), Modifier.PRIVATE).build();
                fields.add(fieldSpec);
            }
            // 构造实体类
            String entityName = classElement.getSimpleName().toString() + "Entity";
            TypeSpec entity = TypeSpec.classBuilder(entityName)
                .addModifiers(Modifier.PUBLIC)
                .addFields(fields)
                .addAnnotation(Data.class) // 使用Lombok
                .build();

            writeJavaFile(classElement, entityName, entity);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void generateRequestEntity(TypeElement classElement, ConvertApi convertApi) {
        try {
            List<String> params = SelectSqlParser.extractPlaceholders(convertApi.sql());
            // 生成请求类（包含WHERE条件参数）
            String requestClassName = classElement.getSimpleName() + convertApi.requestSuffix();
            TypeSpec.Builder requestClass = TypeSpec.classBuilder(requestClassName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Data.class); // Lombok

            params.forEach(param -> {
                String fieldName = convertDatabaseFieldToJava(param); // 转驼峰
                requestClass.addField(FieldSpec.builder(String.class, fieldName)
                    .addModifiers(Modifier.PRIVATE)
                    //                    .addAnnotation(AnnotationSpec.builder(JsonProperty.class).addMember("value", "$S", param).build())
                    .build());
            });
            // 生成Java文件
            writeJavaFile(classElement, requestClassName, requestClass.build());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    //
    //    private void validateFieldMapping(List<String> sqlFields,
    //                                      List<VariableElement> javaFields) {
    //        Set<String> javaFieldNames = javaFields.stream()
    //                .map(e -> e.getSimpleName().toString())
    //                .collect(Collectors.toSet());
    //
    //        sqlFields.forEach(sqlField -> {
    //            String javaStyle = convertDatabaseFieldToJava(sqlField);
    //            if (!javaFieldNames.contains(javaStyle)) {
    //                throw new RuntimeException("字段不匹配: SQL字段[" + sqlField + "] 未在实体类中找到对应属性");
    //            }
    //        });
    //    }
    //

    private void generateMapper(TypeElement classElement, ConvertApi convertApi) {
        try {
            String mapperName = classElement.getSimpleName() + convertApi.mapperSuffix();
            String requestClass = classElement.getSimpleName() + convertApi.requestSuffix();

            TypeSpec mapper = TypeSpec.interfaceBuilder(mapperName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Mapper.class)
                .addMethod(MethodSpec.methodBuilder(lowerFirst(classElement.getSimpleName()
                    .toString()) + "SelectByQuery")
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .addParameter(ParameterSpec.builder(ClassName
                        .get(getPackageName(classElement), requestClass), "query").build())
                    .returns(ParameterizedTypeName.get(ClassName.get(List.class), ClassName
                        .get(getPackageName(classElement), classElement.getSimpleName() + "Entity")))
                    .build())
                .build();

            writeJavaFile(classElement, mapperName, mapper);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void generateXml(TypeElement classElement, ConvertApi convertApi) throws IOException {
        String xmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" + "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\" >\n" + "<mapper namespace=\"%s\">\n" + "  <select id=\"%s\" resultType=\"%s\">\n" + "    %s\n" + "  </select>\n" + "</mapper>";

        String mapperName = classElement.getSimpleName() + convertApi.mapperSuffix();
        String xml = String.format(xmlContent, getPackageName(classElement) + "." + mapperName, lowerFirst(classElement
            .getSimpleName()
            .toString()) + "SelectByQuery", getPackageName(classElement) + "." + classElement
                .getSimpleName() + "Entity", convertApi.sql());

        // 写入resources目录
        String projectRoot = processingEnv.getOptions().get("projectRoot");
        String resourcePath = projectRoot + "/src/main/resources/mapper";
        new File(resourcePath).mkdirs();
        Files.write(Paths.get(resourcePath + "/" + mapperName + ".xml"), xml.getBytes());
    }

    private void generateService(TypeElement classElement, ConvertApi convertApi) {
        try {
            String requestClass = classElement.getSimpleName() + convertApi.requestSuffix();
            String methodName = lowerFirst(classElement.getSimpleName() + "SelectByQuery");
            // 生成请求类（包含WHERE条件参数）
            String requestClassName = classElement.getSimpleName() + convertApi.serviceSuffix();
            TypeSpec.Builder serviceClass = TypeSpec.interfaceBuilder(requestClassName).addModifiers(Modifier.PUBLIC);

            serviceClass.addMethod(MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addParameter(ParameterSpec.builder(ClassName.get(getPackageName(classElement), requestClass), "query")
                    .build())
                .returns(ParameterizedTypeName.get(ClassName.get(List.class), ClassName
                    .get(getPackageName(classElement), classElement.getSimpleName() + "Entity")))
                .build());
            // 生成Java文件
            writeJavaFile(classElement, requestClassName, serviceClass.build());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void generateServiceImpl(TypeElement classElement, ConvertApi convertApi) {
        try {
            String entityElement = classElement.getSimpleName().toString();
            String requestClass = entityElement + convertApi.requestSuffix();
            String serviceInterfaceName = entityElement + convertApi.serviceSuffix();
            String mapperName = entityElement + convertApi.mapperSuffix();

            String methodName = lowerFirst(classElement.getSimpleName() + "SelectByQuery");
            // 生成请求类（包含WHERE条件参数）
            String requestClassName = classElement.getSimpleName() + convertApi.serviceSuffix() + "Impl";
            TypeSpec.Builder serviceClass = TypeSpec.classBuilder(requestClassName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Service.class)
                .addSuperinterface(ClassName.get(getPackageName(classElement), serviceInterfaceName))
                .addField(FieldSpec.builder(ClassName
                    .get(getPackageName(classElement), mapperName), lowerFirst(mapperName), Modifier.PRIVATE)
                    .addAnnotation(Resource.class)
                    .build());

            serviceClass.addMethod(MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC, Modifier.PUBLIC)

                .addParameter(ParameterSpec.builder(ClassName.get(getPackageName(classElement), requestClass), "query")
                    .build())
                .addCode(String.format("return %s.%s(query);", lowerFirst(mapperName), methodName))
                .returns(ParameterizedTypeName.get(ClassName.get(List.class), ClassName
                    .get(getPackageName(classElement), classElement.getSimpleName() + "Entity")))
                .build());
            // 生成Java文件
            writeJavaFile(classElement, requestClassName, serviceClass.build());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void generateController(TypeElement classElement, ConvertApi convertApi) throws IOException {
        String controllerName = classElement.getSimpleName() + "Controller";
        String serviceName = classElement.getSimpleName() + convertApi.serviceSuffix();
        String requestClass = classElement.getSimpleName() + convertApi.requestSuffix();
        String methodName = lowerFirst(classElement.getSimpleName() + "SelectByQuery");

        TypeSpec controller = TypeSpec.classBuilder(controllerName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(RestController.class)
            .addAnnotation(RequiredArgsConstructor.class) // Lombok生成构造器
            .addField(FieldSpec.builder(ClassName
                .get(getPackageName(classElement), serviceName), "service", Modifier.PRIVATE, Modifier.FINAL).build())
            .addMethod(MethodSpec.methodBuilder("query")
                .addModifiers(Modifier.PUBLIC)
                //                        .addAnnotation(AnnotationSpec.builder(PostMapping.class)
                .addAnnotation(AnnotationSpec.builder(GetMapping.class)
                    .addMember("value", "\"" + convertApi.requestPath() + "\"")
                    .build())
                .addParameter(ParameterSpec.builder(ClassName.get(getPackageName(classElement), requestClass), "query")
                    //                                .addAnnotation(RequestBody.class)
                    .build())
                .returns(ParameterizedTypeName.get(ClassName.get(List.class), ClassName
                    .get(getPackageName(classElement), classElement.getSimpleName() + "Entity")))
                .addStatement(String.format("return service.%s(%s)", methodName, "query"))
                .build())
            .build();

        writeJavaFile(classElement, controllerName, controller);
        //            JavaFile.builder(getPackageName(entityElement), controller)
        //                    .build()
        //                    .writeTo(filer);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(ConvertApi.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    private String getPackageName(TypeElement typeElement) {
        return elementUtils.getPackageOf(typeElement).getQualifiedName().toString();
    }

    // 数据库字段转Java属性名（如 user_name -> userName）
    private String convertDatabaseFieldToJava(String dbField) {
        return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, dbField);
    }

    // 首字母小写
    private String lowerFirst(String str) {
        return str.substring(0, 1).toLowerCase() + str.substring(1);
    }

    // 获取项目根路径（关键修改）
    private Path getFileRoot(Element element) {
        // 从编译参数获取项目路径
        String projectRoot = processingEnv.getOptions().get("projectRoot");
        if (projectRoot == null) {
            return null;
        }
        //        String relativePath = getPackageName((TypeElement)element).replace(".", "/");
        Path sourcePath = Paths.get(projectRoot);
        //        return sourcePath.resolve("src/main/java/" + relativePath);
        return sourcePath.resolve("src/main/java/");
    }

    // 生成Java文件到源码目录
    private void writeJavaFile(TypeElement classElement, String className, TypeSpec typeSpec) throws IOException {
        Path filePath = getFileRoot(classElement);
        if (filePath == null) {
            JavaFile.builder(getPackageName(classElement), typeSpec).build().writeTo(filer);
            return;
        }
        Path outputPath = filePath.resolve(className + ".java");
        Files.createDirectories(outputPath.getParent());

        // 检查文件是否存在
        if (!Files.exists(outputPath)) {
            //            messager.printMessage(Diagnostic.Kind.ERROR, "文件不存在 --- " + outputPath);
            //            System.err.println("文件不存在 --- " + outputPath);
            JavaFile.builder(getPackageName(classElement), typeSpec).build().writeTo(filePath);
        }
    }

    // 生成XML文件到resources目录
    private void writeXmlFile(TypeElement classElement, String mapperName, String xmlContent) throws IOException {
        String projectRoot = processingEnv.getOptions().get("projectRoot");
        if (projectRoot == null) {
            projectRoot = "./";
        }
        Path xmlPath = Paths.get(projectRoot).resolve("src/main/resources/mapper").resolve(mapperName + ".xml");

        Files.createDirectories(xmlPath.getParent());
        if (!Files.exists(xmlPath)) {
            Files.write(xmlPath, xmlContent.getBytes(StandardCharsets.UTF_8));
        }
    }
}