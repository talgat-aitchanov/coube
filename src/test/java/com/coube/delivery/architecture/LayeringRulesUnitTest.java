package com.coube.delivery.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.coube.delivery",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class LayeringRulesUnitTest {

    @ArchTest
    static final ArchRule domainModelIsFrameworkFreeAndDoesNotDependOnOtherLayers =
            noClasses().that().resideInAPackage("..model..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "org.hibernate..",
                            "com.coube.delivery.entity..",
                            "com.coube.delivery.controller..",
                            "com.coube.delivery.repository..",
                            "com.coube.delivery.mapper..",
                            "com.coube.delivery.service..",
                            "com.coube.delivery.exception.."
                    );

    @ArchTest
    static final ArchRule jpaEntitiesAreAccessedOnlyByRepositoryMapperAndService =
            classes().that().resideInAPackage("..entity..")
                    .should().onlyBeAccessed().byAnyPackage(
                            "..entity..",
                            "..repository..",
                            "..mapper..",
                            "..service.."
                    );

    @ArchTest
    static final ArchRule controllersDoNotDependOnEntitiesOrRepositories =
            classes().that().resideInAPackage("..controller..").and().haveSimpleNameNotEndingWith("Test")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage(
                            "..controller..",
                            "..service..",
                            "..model..",
                            "..mapper..",
                            "..exception..",
                            "java..",
                            "jakarta.validation..",
                            "org.springframework..",
                            "io.swagger..",
                            "io.micrometer..",
                            "lombok.."
                    );
}
