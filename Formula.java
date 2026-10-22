package com.unipi.mns;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

public class Formula {

    public static  double calculateMaintainabilityIndex(double halsteadVolume, int cyclomaticComplexity, int linesOfCode) {
        return 171 - 5.2 * Math.log(halsteadVolume) - 0.23 * cyclomaticComplexity - 16.2 * Math.log(linesOfCode);
    }

    public static double calculateHalsteadVolume(int n1, int n2, int N1, int N2) {
        if (n1 + n2 == 0) {
            return 1;
        }

        return (N1 + N2) * (Math.log(n1 + n2) / Math.log(2));
    }
}
