package com.jayraj.churnpredictor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

/** Plain JVM tests: the model maths has no Android dependencies. */
public class ChurnModelTest {

    private static ChurnModel.Customer fromGolden(Object[] r) {
        ChurnModel.Customer c = new ChurnModel.Customer();
        c.tenure = (Integer) r[0];
        c.monthlyCharges = (Double) r[1];
        c.contract = Arrays.asList(ChurnModel.CONTRACTS).indexOf(r[2]);
        c.internet = Arrays.asList(ChurnModel.INTERNET).indexOf(r[3]);
        c.payment = Arrays.asList(ChurnModel.PAYMENTS).indexOf(r[4]);
        c.onlineSecurity = (Boolean) r[5];
        c.onlineBackup = (Boolean) r[6];
        c.deviceProtection = (Boolean) r[7];
        c.techSupport = (Boolean) r[8];
        c.streamingTV = (Boolean) r[9];
        c.streamingMovies = (Boolean) r[10];
        c.paperlessBilling = (Boolean) r[11];
        c.seniorCitizen = (Boolean) r[12];
        c.partner = (Boolean) r[13];
        c.dependents = (Boolean) r[14];
        c.phoneService = (Boolean) r[15];
        c.multipleLines = (Boolean) r[16];
        return c;
    }

    private static ChurnModel.Customer base() {
        ChurnModel.Customer c = new ChurnModel.Customer();
        c.tenure = 3;
        c.monthlyCharges = 96.5;
        c.contract = 0;
        c.internet = 1;
        c.payment = 0;
        c.streamingTV = true;
        c.streamingMovies = true;
        c.paperlessBilling = true;
        c.phoneService = true;
        return c;
    }

    @Test
    public void matchesScikitLearnOnRealCustomers() {
        assertTrue(GoldenData.ROWS.length >= 8);
        for (Object[] row : GoldenData.ROWS) {
            double expected = (Double) row[17];
            assertEquals(expected, ChurnModel.probability(fromGolden(row)), 1e-9);
        }
    }

    @Test
    public void probabilityMovesTheWayTheDataShows() {
        ChurnModel.Customer c = base();
        double monthly = ChurnModel.probability(c);
        c.contract = 1;
        double oneYear = ChurnModel.probability(c);
        c.contract = 2;
        double twoYear = ChurnModel.probability(c);
        assertTrue(twoYear < oneYear && oneYear < monthly);

        c = base();
        c.tenure = 60;
        assertTrue(ChurnModel.probability(c) < monthly);

        c = base();
        c.payment = 3;
        assertTrue(ChurnModel.probability(c) < monthly);
    }

    @Test
    public void probabilitiesAreCalibratedOnTheHeldOutSet() {
        for (double[] bin : GoldenData.CALIBRATION) {
            assertTrue("predicted " + bin[0] + " vs observed " + bin[1], Math.abs(bin[0] - bin[1]) < 0.07);
        }
    }

    @Test
    public void bandsFollowTheCutoffs() {
        assertEquals("Low", ChurnModel.band(0.05));
        assertEquals("Moderate", ChurnModel.band(0.20));
        assertEquals("Moderate", ChurnModel.band(0.49));
        assertEquals("High", ChurnModel.band(0.50));
    }

    @Test
    public void driversHaveConsistentSignsAndAreSorted() {
        ChurnModel.Result r = ChurnModel.predict(base());
        assertFalse(r.raising.isEmpty());
        for (ChurnModel.Driver d : r.raising) assertTrue(d.points > 0);
        for (ChurnModel.Driver d : r.lowering) assertTrue(d.points < 0);
        for (int i = 1; i < r.raising.size(); i++) {
            assertTrue(r.raising.get(i - 1).points >= r.raising.get(i).points);
        }
        assertTrue(r.raising.size() <= 3 && r.lowering.size() <= 3);
    }

    @Test
    public void expectedLossIsBillingTimesProbability() {
        ChurnModel.Result r = ChurnModel.predict(base());
        assertEquals(96.5 * 12, r.annualBilling, 1e-9);
        assertEquals(r.annualBilling * r.probability, r.expectedAnnualLoss, 1e-9);
        assertEquals(ChurnModel.probability(base()), r.probability, 1e-12);
    }

    @Test
    public void everyFeatureKeyIsHandled() {
        // vector() throws on an unknown key, so this fails if the model and code drift apart
        assertEquals(ModelData.KEYS.length, ChurnModel.vector(base()).length);
        assertEquals(ModelData.KEYS.length, ModelData.COEF.length);
        assertEquals(ModelData.KEYS.length, ModelData.MEANS.length);
    }
}
