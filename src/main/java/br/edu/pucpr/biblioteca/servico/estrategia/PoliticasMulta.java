package br.edu.pucpr.biblioteca.servico.estrategia;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * R02 — STRATEGY · papéis: STRATEGY ({@link Politica}) e CONCRETE STRATEGIES (Original, ComTeto, ComTolerancia).
 * O CONTEXT é o ServicoMultas, que só conhece a interface Politica.
 *
 * Valores do cenário de verificação (5 dias de atraso):
 *   ORIGINAL     -> R$ 5,00  (R$ 1,00 por dia)
 *   TOLERANCIA_2 -> R$ 3,00  (os 2 primeiros dias não são cobrados: 5 - 2 = 3)
 *   TETO_20      -> R$ 5,00  (só limita quando passaria de R$ 20,00)
 *   sem atraso   -> R$ 0,00 em todas
 *
 * ALTERAÇÃO AO VIVO (nova política): criar uma classe nova que implemente Politica.
 * ServicoMultas, as outras políticas e os boletos já emitidos não mudam (princípio aberto/fechado).
 * Para escolhê-la pela variável BIBLIOTECA_POLITICA, basta registrar mais um case em porNome.
 */
public final class PoliticasMulta {
    private PoliticasMulta() { }

    /** STRATEGY: o contrato que toda política de multa cumpre. */
    public interface Politica {
        /** Nome gravado no boleto na emissão (é o que "congela" qual política foi usada). */
        String nome();
        /** Valor da multa para a quantidade de dias de atraso (nunca negativo). */
        BigDecimal calcular(long diasAtraso);
    }

    /**
     * R$ 1,00 por dia, com 2 casas decimais. max(0, dias) evita valor negativo
     * (necessário na tolerância, quando dias - 2 fica abaixo de zero).
     * BigDecimal em vez de double: dinheiro sem erro de arredondamento binário.
     */
    private static BigDecimal valor(long dias) { return BigDecimal.valueOf(Math.max(0,dias)).setScale(2, RoundingMode.UNNECESSARY); }

    /** CONCRETE STRATEGY: política original do Trabalho 02 — R$ 1,00 por dia de atraso. */
    public static final class Original implements Politica {
        public String nome() { return "ORIGINAL"; }
        public BigDecimal calcular(long dias) { return valor(dias); }
    }
    /** CONCRETE STRATEGY: R$ 1,00 por dia, limitado a R$ 20,00 (min entre o valor e o teto). */
    public static final class ComTeto implements Politica {
        public String nome() { return "TETO_20"; }
        public BigDecimal calcular(long dias) { return valor(dias).min(new BigDecimal("20.00")); }
    }
    /** CONCRETE STRATEGY: tolerância de 2 dias — desconta 2 dias antes de cobrar. */
    public static final class ComTolerancia implements Politica {
        public String nome() { return "TOLERANCIA_2"; }
        public BigDecimal calcular(long dias) { return valor(dias - 2); }
    }

    /**
     * Converte o texto da configuração administrativa (variável BIBLIOTECA_POLITICA) na política.
     * Chamado UMA vez, no Main. Não é o Context: o ServicoMultas nunca vê este switch.
     * Pergunta provável: "então para uma política nova eu mexo aqui?" -> só para ela ser escolhível
     * pela configuração (é o registro de nomes). O cálculo e o serviço não mudam; também é possível
     * trocar em execução com ServicoMultas.definirPolitica(new NovaPolitica()).
     */
    public static Politica porNome(String nome) {
        return switch (nome.toUpperCase()) {
            case "ORIGINAL" -> new Original();
            case "TETO_20" -> new ComTeto();
            case "TOLERANCIA_2" -> new ComTolerancia();
            default -> throw new IllegalArgumentException("Política desconhecida: "+nome);
        };
    }
}
