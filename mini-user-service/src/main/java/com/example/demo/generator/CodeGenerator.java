package com.example.demo.generator;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.DataSourceConfig;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.config.builder.CustomFile;
import com.baomidou.mybatisplus.generator.config.rules.DateType;
import com.baomidou.mybatisplus.generator.config.rules.DbColumnType;
import com.baomidou.mybatisplus.generator.engine.VelocityTemplateEngine;

import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

/**
 * 项目内的 MyBatis-Plus 代码生成器（generator 3.5.7，与运行时同版本）。
 *
 * <p>运行方式：Maven 面板（Profiles -> codegen，再运行 {@code process-classes}
 * 生命周期），或命令行：
 *
 * <pre>
 *   ./mvnw -Pcodegen process-classes
 *   ./mvnw -Pcodegen process-classes -Dgen.tables=sys_user,sys_role -Dgen.outputDir=src/main/java
 * </pre>
 *
 * <p>每张表生成 entity / mapper / mapper.xml / service / serviceImpl / controller，
 * 另外通过 {@code InjectionConfig} 生成四个文件：{@code XxxDTO}、{@code XxxQueryVO}、
 * {@code XxxPageQueryVO} 和 MapStruct 转换器 {@code XxxConverter}（接口 + INSTANCE）。
 * 模板位于 {@code src/main/resources/templates/*.vm}。
 *
 * <p>支持的系统属性（都可选）：
 * <ul>
 *   <li>{@code db.url}、{@code db.username}、{@code db.password}</li>
 *   <li>{@code gen.basePackage}（默认 {@code com.example.demo}）</li>
 *   <li>{@code gen.outputDir}（默认 {@code src/main/java}，可切回 {@code target/generated-sources/mybatis-plus}）</li>
 *   <li>{@code gen.tables}（默认 {@code sys_user}）、{@code gen.tablePrefix}（默认 {@code sys_}）</li>
 *   <li>{@code gen.xmlDir}（默认 {@code src/main/resources/mapper}，一般不用改）</li>
 *   <li>{@code gen.author}、{@code gen.fileOverride}（默认 {@code false}）</li>
 * </ul>
 */
public final class CodeGenerator {

    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/mini_spring_boot"
            + "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai"
            + "&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true";
    private static final String DEFAULT_BASE_PACKAGE = "com.example.demo";

    /**
     * 输出目录，二选一：
     *
     * <p>当前启用 {@code src/main/java}：生成的代码直接落到项目包目录
     * （src/main/java/com/example/demo/...）下；改成
     * {@code target/generated-sources/mybatis-plus} 则只写到构建目录、不污染源码。
     * mapper XML 的落点不受这里影响，见下面的 {@link #DEFAULT_XML_DIR}。
     */
    private static final String DEFAULT_OUTPUT_DIR = "target/generated-sources/mybatis-plus";
//     private static final String DEFAULT_OUTPUT_DIR = "src/main/java";

    /**
     * mapper XML 的输出目录。默认就是 resources 下的 mapper 目录，与 application.yml 里
     * {@code mybatis-plus.mapper-locations: classpath*:/mapper/**} 对应。
     *
     * <p>必须落在这里：XML 若跟着 {@code gen.outputDir} 跑到 src/main/java 下，编译后不在
     * classpath 里，运行时会报
     * {@code BindingException: Invalid bound statement (not found): ...selectListByCondition}。
     * 需要换目录时用 {@code -Dgen.xmlDir=...} 覆盖。
     */
    private static final String DEFAULT_XML_DIR = "src/main/resources/mapper";

    private static final String DEFAULT_TABLES = "sys_user";
    private static final String DEFAULT_TABLE_PREFIX = "sys_";

    /**
     * classpath 下的模板路径。标准（strategy）模板由引擎自动补 ".vm" 后缀，
     * 而 InjectionConfig 自定义文件的路径是原样使用的，所以这里统一写成带 ".vm" 的形式。
     */
    private static final String CONTROLLER_TEMPLATE = "/templates/controller.java.vm";
    private static final String SERVICE_TEMPLATE = "/templates/service.java.vm";
    private static final String SERVICE_IMPL_TEMPLATE = "/templates/serviceImpl.java.vm";
    private static final String MAPPER_TEMPLATE = "/templates/mapper.java.vm";
    private static final String MAPPER_XML_TEMPLATE = "/templates/mapper.xml.vm";
    private static final String DTO_TEMPLATE = "/templates/dto.java.vm";
    private static final String QUERY_VO_TEMPLATE = "/templates/queryVo.java.vm";
    private static final String PAGE_QUERY_VO_TEMPLATE = "/templates/pageQueryVo.java.vm";
    private static final String CONVERTER_TEMPLATE = "/templates/converter.java.vm";

    private CodeGenerator() {
    }

    public static void main(String[] args) {
        String url = property("db.url", DEFAULT_URL);
        String username = property("db.username", "root");
        String password = property("db.password", "root");
        String basePackage = property("gen.basePackage", DEFAULT_BASE_PACKAGE);
        String outputDir = property("gen.outputDir", DEFAULT_OUTPUT_DIR);
        String author = property("gen.author", "mini-spring-cloud-project");
        String tables = property("gen.tables", DEFAULT_TABLES);
        String tablePrefix = property("gen.tablePrefix", DEFAULT_TABLE_PREFIX);
        // mapper XML 的目录：默认 src/main/resources/mapper（必须在 classpath 里才能被扫到）。
        String xmlDir = property("gen.xmlDir", DEFAULT_XML_DIR);
        boolean fileOverride = Boolean.parseBoolean(property("gen.fileOverride", "false"));

        FastAutoGenerator.create(new DataSourceConfig.Builder(url, username, password)
                        // MySQL 的 TINYINT 在 3.5.7 里默认映射成 Byte；本项目的 status/deleted
                        // 在实体、VO、H2 schema 里都是 Integer，这里保持一致。
                        .typeConvertHandler((globalConfig, typeRegistry, metaInfo) ->
                                metaInfo.getJdbcType().TYPE_CODE == Types.TINYINT
                                        ? DbColumnType.INTEGER
                                        : typeRegistry.getColumnType(metaInfo)))
                .globalConfig(builder -> builder
                        .author(author)
                        .outputDir(outputDir)
                        .dateType(DateType.TIME_PACK)
                        .disableOpenDir())
                .packageConfig(builder -> {
                    builder.parent(basePackage)
                            .entity("domain.entity")
                            .service("service")
                            .serviceImpl("service.impl")
                            .mapper("repository")
                            .xml("mapper")
                            .controller("controller");
                    if (xmlDir != null && !xmlDir.trim().isEmpty()) {
                        Map<OutputFile, String> pathInfo = new HashMap<OutputFile, String>(2);
                        pathInfo.put(OutputFile.xml, xmlDir.trim());
                        builder.pathInfo(pathInfo);
                    }
                })
                .strategyConfig(builder -> {
                    builder.addInclude(csv(tables))
                            .addTablePrefix(csv(tablePrefix));
                    builder.entityBuilder()
                            .idType(IdType.AUTO)
                            .enableLombok()
                            .enableTableFieldAnnotation()
                            .logicDeleteColumnName("deleted");
                    builder.controllerBuilder()
                            .enableRestStyle()
                            .template(CONTROLLER_TEMPLATE);
                    // 查询语句统一写在 mapper XML 里（方便改连表），所以 mapper 接口和 XML 都用自定义模板。
                    builder.mapperBuilder()
                            .enableBaseResultMap()
                            .enableBaseColumnList()
                            .mapperTemplate(MAPPER_TEMPLATE)
                            .mapperXmlTemplate(MAPPER_XML_TEMPLATE);
                    // MyBatis-Plus 3.5.7 默认生成 I%sService，本项目统一用 %sService；
                    // list / page 走 service 层（返回 DTO），所以用自定义的 service 模板。
                    builder.serviceBuilder()
                            .formatServiceFileName("%sService")
                            .formatServiceImplFileName("%sServiceImpl")
                            .serviceTemplate(SERVICE_TEMPLATE)
                            .serviceImplTemplate(SERVICE_IMPL_TEMPLATE);
                    if (fileOverride) {
                        builder.entityBuilder().enableFileOverride()
                                .controllerBuilder().enableFileOverride()
                                .serviceBuilder().enableFileOverride()
                                .mapperBuilder().enableFileOverride();
                    }
                })
                .injectionConfig(builder -> builder
                        .customFile(file -> customFile(file, "DTO.java", DTO_TEMPLATE, "domain.dto", fileOverride))
                        .customFile(file -> customFile(file, "QueryVO.java", QUERY_VO_TEMPLATE, "domain.query", fileOverride))
                        .customFile(file -> customFile(file, "PageQueryVO.java", PAGE_QUERY_VO_TEMPLATE, "domain.query", fileOverride))
                        .customFile(file -> customFile(file, "Converter.java", CONVERTER_TEMPLATE, "convert", fileOverride)))
                .templateEngine(new VelocityTemplateEngine())
                .execute();

        System.out.println("[codegen] tables=" + tables + ", outputDir=" + outputDir
                + ", xmlDir=" + xmlDir + ", fileOverride=" + fileOverride);
    }

    /**
     * CustomFile 的文件名是 {@code <实体名> + fileName}，packageName 会拼在
     * {@code packageConfig.parent} 下面（例如 domain.dto -> .../domain/dto）。
     */
    private static void customFile(CustomFile.Builder builder, String fileNameSuffix, String template,
                                   String packageName, boolean fileOverride) {
        builder.fileName(fileNameSuffix)
                .templatePath(template)
                .packageName(packageName);
        if (fileOverride) {
            builder.enableFileOverride();
        }
    }

    private static String[] csv(String value) {
        String[] parts = value.split(",");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return parts;
    }

    private static String property(String key, String defaultValue) {
        // 判断用 "value == null" 而不是 isBlank：-Ddb.password= 要能表示"空密码"。
        String value = System.getProperty(key);
        return value == null ? defaultValue : value.trim();
    }
}