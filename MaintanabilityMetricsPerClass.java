package com.unipi.mns;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class MaintanabilityMetricsPerClass {
    private int totalCyclomaticComplexity = 0;
    private double totalHalsteadVolume = 0;
    private double totalMaintainabilityIndex = 0;
    private int totalClasses = 0;
    private int totalCoupling = 0;
    private double totalLackOfCohesion = 0.0;
    private int totalLinesOfCode = 0;

    public static void main(String[] args) {
        MaintanabilityMetricsPerClass calculator = new MaintanabilityMetricsPerClass();
        List<File> javaFiles = new ArrayList<>();

        boolean goodImplementation = true;

        String directoryPath = "replace-with-your-actual-project-directory";

        DirectoryTraversal.findJavaFiles(new File(directoryPath), javaFiles);

        for (File javaFile : javaFiles) {
            try {
                calculator.calculateMetricsForFile(javaFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        System.out.println("Total number of classes: " + calculator.totalClasses);
        System.out.println("Total lines of code: " + calculator.totalLinesOfCode);
        System.out.printf("Avg lines of code per class: %.2f \n", ((double) calculator.totalLinesOfCode / calculator.totalClasses));
        System.out.println("Total Cyclomatic Complexity: " + calculator.totalCyclomaticComplexity);
        System.out.printf("Total Halstead Volume: %.2f \n", calculator.totalHalsteadVolume);
        System.out.printf("Average Maintainability Index: %.2f \n", (calculator.totalMaintainabilityIndex / calculator.totalClasses));
        System.out.printf("Average CBO: %.2f \n", ((double) calculator.totalCoupling / calculator.totalClasses));
        System.out.printf("Average LCOM: %.2f \n", (calculator.totalLackOfCohesion / calculator.totalClasses));
    }


    public void calculateMetricsForFile(File javaFile) throws IOException {
        FileInputStream in = new FileInputStream(javaFile);
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(in).getResult().orElseThrow(() -> new IOException("Failed to parse file"));

        List<String> allLines = new BufferedReader(new InputStreamReader(new FileInputStream(javaFile))).lines().collect(Collectors.toList());

        int loc = (int) allLines.stream()
                .filter(line -> !line.trim().isEmpty())
                .filter(line -> !line.trim().startsWith("//"))
                .filter(line -> !line.trim().startsWith("/*"))
                .filter(line -> !line.trim().endsWith("*/"))
                .filter(line -> !line.trim().startsWith("*"))
                .count();

        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDefinition -> {
            totalClasses++;
            calculateMetricsForClass(classDefinition, loc);
        });
    }

    private void calculateMetricsForClass(ClassOrInterfaceDeclaration classDefinition, int linesOfCode) {
        int classCyclomaticComplexity = 0;
        int classTotalOperators = 0;
        int classTotalOperands = 0;
        Set<String> classDistinctOperators = new HashSet<>();
        Set<String> classDistinctOperands = new HashSet<>();

        for (MethodDeclaration method : classDefinition.getMethods()) {
            classCyclomaticComplexity += calculateCyclomaticComplexity(method);

            classTotalOperators += calculateHalsteadOperators(method, classDistinctOperators);
            classTotalOperands += calculateHalsteadOperands(method, classDistinctOperands);
        }

        for (ConstructorDeclaration constr : classDefinition.getConstructors()) {
            classCyclomaticComplexity += calculateCyclomaticComplexity(constr);

            classTotalOperators += calculateHalsteadOperators(constr, classDistinctOperators);
            classTotalOperands += calculateHalsteadOperands(constr, classDistinctOperands);
        }

        int cbo = calculateCBO(classDefinition);
        double lcom = calculateLCOM(classDefinition);

        double halsteadVolume = Formula.calculateHalsteadVolume(classDistinctOperators.size(), classDistinctOperands.size(), classTotalOperators, classTotalOperands);
        double maintainabilityIndex = Formula.calculateMaintainabilityIndex(halsteadVolume, classCyclomaticComplexity, linesOfCode);

        System.out.println("Class: " + classDefinition.getNameAsString() + " with LOC = " + linesOfCode);
        System.out.println("Cyclomatic Complexity: " + classCyclomaticComplexity);
        System.out.printf("Halstead Volume: %.2f \n", halsteadVolume);
        System.out.printf("Maintainability Index: %.2f \n", maintainabilityIndex);
        System.out.println("Coupling Between Objects (CBO): " + cbo);
        System.out.println("Lack of Cohesion of Methods (LCOM): " + lcom);
        System.out.println();

        // Aggregation
        totalCyclomaticComplexity += classCyclomaticComplexity;
        totalHalsteadVolume += halsteadVolume;
        totalMaintainabilityIndex += maintainabilityIndex;
        totalCoupling += cbo;
        totalLackOfCohesion += lcom;
        totalLinesOfCode += linesOfCode;
    }

    private int calculateCyclomaticComplexity(Node method) {
        return (int) method.findAll(com.github.javaparser.ast.stmt.Statement.class).stream()
                .filter(stmt -> stmt.isIfStmt() || stmt.isForStmt() || stmt.isWhileStmt() || stmt.isSwitchStmt() || stmt.isDoStmt())
                .count() + 1;
    }

    private int calculateHalsteadOperands(Node method, Set<String> distinctOperands) {
        AtomicInteger totalOperands = new AtomicInteger();

        method.findAll(NameExpr.class).forEach(expr -> {
            distinctOperands.add(expr.getNameAsString());
            totalOperands.getAndIncrement();
        });

        method.findAll(NameExpr.class).forEach(expr -> {
            distinctOperands.add(expr.getNameAsString());
            totalOperands.getAndIncrement();
        });

        method.findAll(LiteralExpr.class).forEach(expr -> {
            distinctOperands.add(expr.toString());
            totalOperands.getAndIncrement();
        });

        method.findAll(VariableDeclarator.class).forEach(decl -> {
            distinctOperands.add(decl.getNameAsString());
            totalOperands.getAndIncrement();
        });

        return totalOperands.get();
    }

    private int calculateHalsteadOperators(Node method, Set<String> distinctOperators) {
        AtomicInteger totalOperators = new AtomicInteger();

        method.findAll(BinaryExpr.class).forEach(expr -> {
            distinctOperators.add(expr.getOperator().asString());
            totalOperators.getAndIncrement();
        });

        method.findAll(UnaryExpr.class).forEach(expr -> {
            distinctOperators.add(expr.getOperator().asString());
            totalOperators.getAndIncrement();
        });

        method.findAll(AssignExpr.class).forEach(expr -> {
            distinctOperators.add(expr.getOperator().asString());
            totalOperators.getAndIncrement();
        });

        method.findAll(MethodCallExpr.class).forEach(expr -> {
            distinctOperators.add(expr.getNameAsString());
            totalOperators.getAndIncrement();
        });

        method.findAll(ExplicitConstructorInvocationStmt.class).forEach(expr -> {
            distinctOperators.add(expr.toString());
            totalOperators.getAndIncrement();
        });

        method.findAll(ConditionalExpr.class).forEach(expr -> {
            distinctOperators.add("?:");
            totalOperators.getAndIncrement();
        });

        return totalOperators.get();
    }

    private int calculateCBO(ClassOrInterfaceDeclaration classDefinition) {
        Set<String> coupledClasses = new HashSet<>();

        classDefinition.findAll(MethodCallExpr.class).forEach(methodCall -> {
            if (methodCall.getScope().isPresent()) {
                String scope = methodCall.getScope().get().toString();
                if (isClassName(scope)) {
                    coupledClasses.add(scope);
                }
            }
        });

        classDefinition.findAll(FieldAccessExpr.class).forEach(fieldAccess -> {
            String scope = fieldAccess.getScope().toString();
            if (isClassName(scope)) {
                coupledClasses.add(scope);
            }
        });

        classDefinition.findAll(ObjectCreationExpr.class).forEach(objectCreation -> {
            String typeName = objectCreation.getType().asString();
            if (isClassName(typeName)) {
                coupledClasses.add(stripGenerics(typeName));
            }

            extractGenericClasses(typeName).forEach(className -> {
                if (isClassName(className)) {
                    coupledClasses.add(className);
                }
            });
        });

        classDefinition.findAll(MethodDeclaration.class).forEach(method -> {
            String returnType = method.getType().asString();
            if (isClassName(returnType)) {
                coupledClasses.add(stripGenerics(returnType));
            }

            extractGenericClasses(returnType).forEach(className -> {
                if (isClassName(className)) {
                    coupledClasses.add(className);
                }
            });

            method.getParameters().forEach(parameter -> {
                String parameterType = parameter.getType().asString();
                if (isClassName(parameterType)) {
                    coupledClasses.add(stripGenerics(parameterType));
                }

                extractGenericClasses(parameterType).forEach(className -> {
                    if (isClassName(className)) {
                        coupledClasses.add(className);
                    }
                });
            });
        });

        classDefinition.findAll(FieldDeclaration.class).forEach(field -> {
            field.getAnnotations().forEach(annotation -> {
                if (annotation.getName().asString().equals("Autowired")) {
                    String fieldType = field.getElementType().asString();
                    if (isClassName(fieldType)) {
                        coupledClasses.add(stripGenerics(fieldType));
                    }

                    extractGenericClasses(fieldType).forEach(className -> {
                        if (isClassName(className)) {
                            coupledClasses.add(className);
                        }
                    });
                }
            });
        });

        coupledClasses.remove(classDefinition.getNameAsString());
        return coupledClasses.size();
    }

    private double calculateLCOM(ClassOrInterfaceDeclaration classDefinition) {
        List<Set<String>> fieldsUsedPerMethod = new ArrayList<>();
        Set<String> allFieldsUsed = new HashSet<>();

        classDefinition.findAll(VariableDeclarator.class).forEach(variable -> {
            allFieldsUsed.add(variable.getNameAsString());
        });

        for (MethodDeclaration method : classDefinition.getMethods()) {
            Set<String> accessedFields = new HashSet<>();
            method.findAll(NameExpr.class).forEach(nameExpr -> {
                if (allFieldsUsed.contains(nameExpr.getNameAsString())) {
                    accessedFields.add(nameExpr.getNameAsString());
                }
            });
            fieldsUsedPerMethod.add(accessedFields);
        }

        int methodPairs = 0;
        int methodPairsWithSharedFields = 0;

        for (int i = 0; i < fieldsUsedPerMethod.size(); i++) {
            for (int j = i + 1; j < fieldsUsedPerMethod.size(); j++) {
                methodPairs++;

                Set<String> intersection = new HashSet<>(fieldsUsedPerMethod.get(i));
                intersection.retainAll(fieldsUsedPerMethod.get(j));

                if (intersection.isEmpty()) {
                    continue;
                }

                methodPairsWithSharedFields++;
            }
        }

        return methodPairs == 0 ? 0.0 : 1 - (double) methodPairsWithSharedFields / methodPairs;
    }

    private String extractClassName(String expression) {
        return expression.contains(".") ?
            expression.substring(0, expression.indexOf('.'))
            :
            expression;
    }

    private String stripGenerics(String typeName) {
        int genericStart = typeName.indexOf('<');

        if (genericStart != -1) {
            return typeName.substring(0, genericStart);
        }

        return typeName;
    }

    private boolean isClassName(String name) {
        return name != null &&
                !name.isBlank() &&
                !name.contains(".") &&
                name.length() > 1 &&
                Character.isUpperCase(name.charAt(0)) &&
                !isExcludedClass(name);
    }

    private Set<String> extractGenericClasses(String typeName) {
        Set<String> genericClasses = new HashSet<>();
        int genericStart = typeName.indexOf('<');
        int genericEnd = typeName.lastIndexOf('>');

        if (genericStart != -1 && genericEnd != -1) {
            String genericContent = typeName.substring(genericStart + 1, genericEnd);
            String[] genericTypes = genericContent.split(",");
            for (String genericType : genericTypes) {
                genericClasses.add(genericType.trim());
            }
        }

        return genericClasses;
    }

    private boolean isExcludedClass(String name) {
        return name.equals("String") || name.equals("Integer") || name.equals("Double") ||
                name.equals("Boolean") || name.equals("Character") || name.equals("Byte") ||
                name.equals("Short") || name.equals("Long") || name.equals("Float") ||
                name.equals("Void") || name.equals("Date") || name.equals("DateTime") ||
                name.equals("LocalDate") || name.equals("LocalDateTime");
    }
}
