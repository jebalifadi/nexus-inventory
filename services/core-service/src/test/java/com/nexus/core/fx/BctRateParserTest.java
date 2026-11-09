package com.nexus.core.fx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** The page's layout, with the interbank table first and the cash exchange table after it. */
class BctRateParserTest {

    private static final String PAGE = """
            <h3>Cours Moyens des Devises Cot&eacute;es en dinar tunisien<br>sur le Marché des Changes interbancaire</h3>
            <h5>(Opérations en Compte et au Comptant)</h5><h5>Journée du 05/11/2026</h5>
            <table><tr><td>DOLLAR DES USA</td><td>USD</td><td>1</td><td>3,0107</td></tr>
            <tr><td>YEN JAPONAIS</td><td>JPY</td><td>1000</td><td>18,8314</td></tr>
            <tr><td>EURO</td><td>EUR</td><td>1</td><td>3,3832</td></tr></table>
            <h3>Cours Moyens en dinar tunisien pour le Change Manuel et les Chèques de Voyage</h3>
            <h5>Journée du 05/11/2026</h5>
            <table><tr><td>EURO</td><td>EUR</td><td>1</td><td>3,342</td></tr></table>
            """;

    @Test
    void readsTheInterbankTableAndNotTheCashOne() {
        BankRates rates = BctRateParser.parse(PAGE);

        assertThat(rates.date()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(rates.dinarsPerUnit()).containsOnlyKeys("EUR", "USD");
        assertThat(rates.dinarsPerUnit().get("EUR")).isEqualByComparingTo("3.3832");
        assertThat(rates.dinarsPerUnit().get("USD")).isEqualByComparingTo("3.0107");
    }

    @Test
    void refusesAPageItDoesNotRecognise() {
        assertThatThrownBy(() -> BctRateParser.parse("<html>Maintenance</html>"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BctRateParser.parse(PAGE.replace("interbancaire", "autre")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BctRateParser.parse(PAGE.replace("3,3832", "338,32")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Implausible");
    }
}
