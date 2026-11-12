package com.expensetracker.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TransactionSearchSpecsTest {

    @Test
    void nullOrBlankTextMeansNoFilter() {
        assertNull(TransactionSearchSpecs.descriptionContains(null));
        assertNull(TransactionSearchSpecs.descriptionContains(""));
        assertNull(TransactionSearchSpecs.descriptionContains("   "));
        assertNotNull(TransactionSearchSpecs.descriptionContains("lunch"));
    }

    @Test
    void patternIsLowerCaseTrimmedAndWrapped() {
        assertEquals("%lunch%", TransactionSearchSpecs.likePattern("Lunch"));
        assertEquals("%coffee shop%", TransactionSearchSpecs.likePattern("  Coffee Shop "));
    }

    @Test
    void wildcardCharactersInTheSearchTextAreEscaped() {
        // a user typing "50%_off!" must find that exact text, not "50<anything>off"
        assertEquals("%50!%!_off!!%", TransactionSearchSpecs.likePattern("50%_off!"));
    }
}
