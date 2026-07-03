package com.foodmate.bootstrap.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.foodmate", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleDependencyArchTest {
    @ArchTest
    static final ArchRule apiMustStayThin = noClasses()
            .that().resideInAPackage("com.foodmate.api..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "com.foodmate.infrastructure..",
                    "com.foodmate.model..",
                    "com.foodmate.rag.."
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

