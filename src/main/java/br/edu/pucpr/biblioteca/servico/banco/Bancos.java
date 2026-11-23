package br.edu.pucpr.biblioteca.servico.banco;

import br.edu.pucpr.biblioteca.dominio.*;
import br.edu.pucpr.biblioteca.dominio.Entidades.*;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * R03 — ADAPTER · papéis:
 *   TARGET   = {@link CobrancaInstitucional} (o contrato que o ServicoMultas conhece)
 *   ADAPTEES = {@link ApiBancoA} e {@link ApiBancoB} (APIs incompatíveis, simuladas como o enunciado permite)
 *   ADAPTERS = {@link AdaptadorBancoA} e {@link AdaptadorBancoB}
 *   CLIENT   = ServicoMultas
 *
 * Onde ficam as conversões (pergunta da arguição)? SÓ nos adaptadores:
 *                  Banco A                       Banco B
 *   valor          centavos (int)  700           reais (BigDecimal) 7.00
 *   CPF            só dígitos      12345678900   máscara            123.456.789-00
 *                  (aceita qualquer entrada:     (exige 11 dígitos; formata a partir
 *                   remove o que não é dígito)    de qualquer entrada, com ou sem máscara)
 *   identificador  "A-" + id do boleto           "B-" + id do boleto
 *   pago           código "00"                   texto "LIQUIDADO"
 *
 * Como o banco da instalação é escolhido? No Main, pela variável BIBLIOTECA_BANCO (A ou B).
 * Banco novo = ApiBancoC + AdaptadorBancoC + um case no Main. O ServicoMultas não muda.
 */
public final class Bancos {
    private Bancos() { }

    /** TARGET: contrato institucional de cobrança (estende a interface iCobranca do Trabalho 02). */
    public interface CobrancaInstitucional extends br.edu.pucpr.biblioteca.api.Interfaces.iCobranca {
        /** Registra a cobrança do boleto no banco e devolve o identificador externo. */
        Cobranca cobrar(Boleto boleto,String cpf);
        /** Traduz a notificação do banco para a situação institucional: "PAGO" ou "PENDENTE". */
        ConfirmacaoPagamento notificar(String identificadorBoleto,String situacaoExterna);
    }

    /** ADAPTEE A (simulado): recebe valor em CENTAVOS e documento só com números. */
    public static final class ApiBancoA {
        public int ultimosCentavos;      // campos públicos só para o teste conferir o que chegou ao "banco"
        public String ultimoDocumento;
        public String remessa(int centavos,String documentoNumerico,String referencia) {
            this.ultimosCentavos=centavos; this.ultimoDocumento=documentoNumerico;
            return "A-"+referencia;
        }
    }
    /** ADAPTEE B (simulado): recebe valor em REAIS (BigDecimal) e documento formatado. */
    public static final class ApiBancoB {
        public BigDecimal ultimosReais;
        public String ultimoDocumento;
        public String emitir(BigDecimal reais,String documentoFormatado,String referencia) {
            this.ultimosReais=reais;this.ultimoDocumento=documentoFormatado;
            return "B-"+referencia;
        }
    }

    /** ADAPTER A: converte o contrato institucional para a API do banco A. */
    public static final class AdaptadorBancoA implements CobrancaInstitucional {
        private final ApiBancoA api;   // composição: o adaptador EMBRULHA a API (object adapter)
        public AdaptadorBancoA(ApiBancoA api) { this.api=api; }
        public Cobranca cobrar(Boleto b,String cpf) {
            // R$ 7,00 -> 700: move a vírgula 2 casas; intValueExact lança erro se sobrar fração (nunca perde centavo)
            int centavos=b.valor().movePointRight(2).intValueExact();
            // CPF: remove tudo que não é dígito ("123.456.789-00" -> "12345678900")
            return new Cobranca(api.remessa(centavos,cpf.replaceAll("\\D",""),b.id()),"A");
        }
        /** Tradução da notificação: no banco A, código "00" significa pago; qualquer outro é pendente. */
        public ConfirmacaoPagamento notificar(String id,String codigo) {
            return new ConfirmacaoPagamento(id, "00".equals(codigo)?"PAGO":"PENDENTE");
        }
    }
    /**
     * ADAPTER B: o banco B aceita o valor em reais (BigDecimal) e exige o CPF COM MÁSCARA.
     * Conversões: documento (qualquer formato -> 000.000.000-00) e situação da notificação.
     */
    public static final class AdaptadorBancoB implements CobrancaInstitucional {
        private final ApiBancoB api;
        public AdaptadorBancoB(ApiBancoB api) { this.api=api; }
        public Cobranca cobrar(Boleto b,String cpf) {
            return new Cobranca(api.emitir(b.valor(),formatarCpf(cpf),b.id()),"B");
        }
        /**
         * Converte o CPF para o formato do banco B, venha ele com ou sem máscara:
         * "12345678900" ou "123.456.789-00" -> "123.456.789-00".
         * Sem 11 dígitos não dá para montar a máscara: o adaptador recusa em vez de mandar um documento inválido ao banco.
         */
        static String formatarCpf(String cpf) {
            String d=cpf==null?"":cpf.replaceAll("\\D","");
            if(d.length()!=11)throw new IllegalArgumentException("CPF inválido para o Banco B: "+cpf);
            return d.substring(0,3)+"."+d.substring(3,6)+"."+d.substring(6,9)+"-"+d.substring(9);
        }
        /** Tradução da notificação: no banco B, "LIQUIDADO" significa pago. */
        public ConfirmacaoPagamento notificar(String id,String situacao) {
            return new ConfirmacaoPagamento(id,"LIQUIDADO".equalsIgnoreCase(situacao)?"PAGO":"PENDENTE");
        }
    }
}
