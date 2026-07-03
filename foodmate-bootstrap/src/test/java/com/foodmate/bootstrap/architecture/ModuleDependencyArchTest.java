package com.foodmate.bootstrap.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 模块依赖约束测试，防止分层边界被破坏。
 */
@AnalyzeClasses(packages = "com.foodmate", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleDependencyArchTest {
    @ArchTest
    static final ArchRule apiMustStayThin = noClasses()
            .that().resideInAPackage("com.foodmate.api..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "com.foodmate.infrastructure..",
                    "com.foodmate.model..",
                    "com.foodmate.orchestrator..",
                    "com.foodmate.rag..",
                    "com.foodmate.sqlagent..",
                    "com.foodmate.tool..",
                    "com.foodmate.worker.."
            );

    @ArchTest
    static final ArchRule useCasesMustNotBypassInfrastructurePorts = noClasses()
            .that().resideInAnyPackage(
                    "com.foodmate.application..",
                    "com.foodmate.orchestrator.."
            )
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "com.foodmate.infrastructure..",
                    "com.baomidou.mybatisplus..",
                    "org.apache.ibatis..",
                    "org.mybatis.."
            );

    @ArchTest
    static final ArchRule sharedMustNotDependOnBusinessModules = noClasses()
            .that().resideInAPackage("com.foodmate.shared..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "com.foodmate.api..",
                    "com.foodmate.application..",
                    "com.foodmate.domain..",
                    "com.foodmate.infrastructure..",
                    "com.foodmate.orchestrator..",
                    "com.foodmate.rag..",
                    "com.foodmate.sqlagent..",
                    "com.foodmate.tool..",
                    "com.foodmate.model..",
                    "com.foodmate.worker.."
            );

    @ArchTest
    static final ArchRule mybatisPlusMustStayInInfrastructure = noClasses()
            .that().resideOutsideOfPackage("com.foodmate.infrastructure..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "com.baomidou.mybatisplus..",
                    "org.apache.ibatis..",
                    "org.mybatis.."
            );
}
