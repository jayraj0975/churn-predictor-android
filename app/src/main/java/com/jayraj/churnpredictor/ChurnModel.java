package com.jayraj.churnpredictor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Scores a customer with the exported logistic regression. No Android classes are
 * used here, so the maths runs in plain JVM unit tests and can be checked against
 * scikit-learn's own predictions.
 *
 * sigmoid(intercept + sum(coefficient * value)), where the coefficients are in raw
 * feature units (see {@link ModelData}).
 */
public final class ChurnModel {
    private ChurnModel() {}

    public static final String[] CONTRACTS = {"Month-to-month", "One year", "Two year"};
    public static final String[] INTERNET = {"DSL", "Fiber optic", "No"};
    public static final String[] INTERNET_LABELS = {"DSL", "Fiber optic", "No internet"};
    public static final String[] PAYMENTS = {
        "Electronic check", "Mailed check", "Bank transfer (automatic)", "Credit card (automatic)"};

    /** Risk bands on the predicted probability. The average customer churns about 27% of the time. */
    public static final double MODERATE_FROM = 0.20;
    public static final double HIGH_FROM = 0.50;

    /** One customer. Contract, internet and payment are indexes into the arrays above. */
    public static final class Customer {
        public int tenure;                 // months
        public double monthlyCharges;      // dollars
        public int contract;
        public int internet;
        public int payment;
        public boolean onlineSecurity, onlineBackup, deviceProtection, techSupport,
                streamingTV, streamingMovies, paperlessBilling, seniorCitizen,
                partner, dependents, phoneService, multipleLines;
    }

    /** How far one factor moves this customer's predicted churn, in percentage points. */
    public static final class Driver {
        public final String label;
        public final double points;

        Driver(String label, double points) {
            this.label = label;
            this.points = points;
        }
    }

    public static final class Result {
        public final double probability;
        public final String band;
        public final String recommendation;
        public final List<Driver> raising;
        public final List<Driver> lowering;
        public final double annualBilling;
        public final double expectedAnnualLoss;

        Result(double probability, String band, String recommendation,
               List<Driver> raising, List<Driver> lowering, double annualBilling) {
            this.probability = probability;
            this.band = band;
            this.recommendation = recommendation;
            this.raising = raising;
            this.lowering = lowering;
            this.annualBilling = annualBilling;
            this.expectedAnnualLoss = annualBilling * probability;
        }
    }

    private static double sigmoid(double z) {
        return 1.0 / (1.0 + Math.exp(-z));
    }

    /** The customer as the model's numeric feature vector, in {@link ModelData#KEYS} order. */
    static double[] vector(Customer c) {
        double[] x = new double[ModelData.KEYS.length];
        for (int i = 0; i < x.length; i++) {
            x[i] = value(c, ModelData.KEYS[i]);
        }
        return x;
    }

    private static double value(Customer c, String key) {
        switch (key) {
            case "tenure": return c.tenure;
            case "monthlyCharges": return c.monthlyCharges;
            case "seniorCitizen": return c.seniorCitizen ? 1 : 0;
            case "partner": return c.partner ? 1 : 0;
            case "dependents": return c.dependents ? 1 : 0;
            case "phoneService": return c.phoneService ? 1 : 0;
            case "multipleLines": return c.multipleLines ? 1 : 0;
            case "onlineSecurity": return c.onlineSecurity ? 1 : 0;
            case "onlineBackup": return c.onlineBackup ? 1 : 0;
            case "deviceProtection": return c.deviceProtection ? 1 : 0;
            case "techSupport": return c.techSupport ? 1 : 0;
            case "streamingTV": return c.streamingTV ? 1 : 0;
            case "streamingMovies": return c.streamingMovies ? 1 : 0;
            case "paperlessBilling": return c.paperlessBilling ? 1 : 0;
            case "contractOneYear": return c.contract == 1 ? 1 : 0;
            case "contractTwoYear": return c.contract == 2 ? 1 : 0;
            case "internetFiber": return c.internet == 1 ? 1 : 0;
            case "internetNone": return c.internet == 2 ? 1 : 0;
            case "payMailedCheck": return c.payment == 1 ? 1 : 0;
            case "payBankTransfer": return c.payment == 2 ? 1 : 0;
            case "payCreditCard": return c.payment == 3 ? 1 : 0;
            default: throw new IllegalArgumentException("Unknown feature " + key);
        }
    }

    private static double logit(double[] x) {
        double z = ModelData.INTERCEPT;
        for (int i = 0; i < x.length; i++) {
            z += ModelData.COEF[i] * x[i];
        }
        return z;
    }

    /** Predicted churn probability in [0, 1]. */
    public static double probability(Customer c) {
        return sigmoid(logit(vector(c)));
    }

    public static String band(double probability) {
        if (probability >= HIGH_FROM) return "High";
        if (probability >= MODERATE_FROM) return "Moderate";
        return "Low";
    }

    public static Result predict(Customer c) {
        double[] x = vector(c);
        double z = logit(x);
        double p = sigmoid(z);

        // Contribution of each factor relative to the average customer, in points of
        // probability. Association learned from past customers, not proven cause.
        List<Driver> all = new ArrayList<>();
        boolean[] used = new boolean[x.length];

        addGroup(all, x, z, used, "Contract: " + CONTRACTS[c.contract], "contractOneYear", "contractTwoYear");
        addGroup(all, x, z, used, "Internet: " + INTERNET_LABELS[c.internet], "internetFiber", "internetNone");
        addGroup(all, x, z, used, "Payment: " + PAYMENTS[c.payment], "payMailedCheck", "payBankTransfer", "payCreditCard");
        for (int i = 0; i < x.length; i++) {
            if (used[i]) continue;
            String label = ModelData.LABELS[i].replaceAll(" \\(.*\\)", "");
            String shown = ModelData.KEYS[i].equals("tenure") ? c.tenure + " months"
                    : ModelData.KEYS[i].equals("monthlyCharges") ? String.format(Locale.US, "$%.2f/mo", c.monthlyCharges)
                    : x[i] > 0 ? "yes" : "no";
            add(all, label + ": " + shown, ModelData.COEF[i] * (x[i] - ModelData.MEANS[i]), z);
        }

        List<Driver> raising = new ArrayList<>();
        List<Driver> lowering = new ArrayList<>();
        for (Driver d : all) {
            (d.points > 0 ? raising : lowering).add(d);
        }
        Collections.sort(raising, (a, b) -> Double.compare(b.points, a.points));
        Collections.sort(lowering, (a, b) -> Double.compare(a.points, b.points));

        double annual = c.monthlyCharges * 12.0;
        return new Result(p, band(p), recommend(c, band(p)),
                top(raising, 3), top(lowering, 3), annual);
    }

    private static void addGroup(List<Driver> out, double[] x, double z, boolean[] used,
                                 String label, String... keys) {
        double contribution = 0;
        for (String key : keys) {
            int i = indexOf(key);
            used[i] = true;
            contribution += ModelData.COEF[i] * (x[i] - ModelData.MEANS[i]);
        }
        add(out, label, contribution, z);
    }

    private static void add(List<Driver> out, String label, double contribution, double z) {
        double points = Math.round((sigmoid(z) - sigmoid(z - contribution)) * 1000.0) / 10.0;
        if (Math.abs(points) >= 0.5) {
            out.add(new Driver(label, points));
        }
    }

    private static int indexOf(String key) {
        for (int i = 0; i < ModelData.KEYS.length; i++) {
            if (ModelData.KEYS[i].equals(key)) return i;
        }
        throw new IllegalArgumentException("Unknown feature " + key);
    }

    private static List<Driver> top(List<Driver> sorted, int n) {
        return new ArrayList<>(sorted.subList(0, Math.min(n, sorted.size())));
    }

    /** Plain rules keyed off the profile. These are playbook suggestions, not model output. */
    static String recommend(Customer c, String band) {
        if (band.equals("High")) {
            if (c.contract == 0 && !c.techSupport) {
                return "High risk: offer a 1-year contract with tech support included, and check the price is competitive.";
            }
            if (c.payment == 0) {
                return "High risk: offer a small credit for moving to automatic payment, paired with a contract-term discount.";
            }
            return "High risk: proactive outreach from customer success, with a plan review and loyalty pricing.";
        }
        if (band.equals("Moderate")) {
            if (c.contract == 0) {
                return "Moderate risk: offer an annual-plan discount.";
            }
            if (!c.onlineSecurity && c.internet != 2) {
                return "Moderate risk: bundle online security and backup to deepen usage.";
            }
            return "Moderate risk: send a satisfaction survey and check recent support tickets were resolved.";
        }
        return "Low predicted risk. Keep the regular check-in cadence.";
    }
}
