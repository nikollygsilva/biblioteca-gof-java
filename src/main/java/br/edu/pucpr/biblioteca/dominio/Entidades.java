package br.edu.pucpr.biblioteca.dominio;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Objetos de valor imutáveis (records) do domínio, usados pelos três pontos de acesso
 * (Acervo, Relacionamento, Movimentacao) e pelos repositórios.
 * Por serem imutáveis, podem ser compartilhados sem cópia e entregues à interface sem risco.
 */
public final class Entidades {
    private Entidades() { }

    /** Pessoa física, identificada pelo CPF. Estudante e Atendente apontam para ela pelo CPF. */
    public record Pessoa(String cpf, String nome, String sobrenome, String email) {
        public Pessoa { Objects.requireNonNull(cpf); Objects.requireNonNull(nome); Objects.requireNonNull(email); }
    }
    /** Estudante: pré-requisito do cadastro (R07) é existir uma Pessoa com este CPF. */
    public record Estudante(String id, String cpf) { }
    /** Atendente: mesmo pré-requisito do Estudante. */
    public record Atendente(String id, String cpf) { }
    /** Reserva ativa de um livro por um estudante. É consumida (removida) quando vira empréstimo (R04). */
    public record Reserva(String idLivro, String idEstudante, Instant criadaEm) { }

    /**
     * Devolução registrada. É também o conteúdo do evento do Observer (R05):
     * leva vencimento, data de devolução e estudante, para os ouvintes não precisarem consultar nada depois.
     */
    public record Devolucao(String id, String idLivro, String idEstudante,
                            LocalDate vencimento, LocalDate dataDevolucao) {
        /**
         * Pergunta do R02: "como os dias de atraso são calculados?"
         *   -> diferença em dias corridos entre vencimento e devolução; devolução no prazo ou adiantada = 0
         *      (o max evita atraso negativo).
         */
        public long diasAtraso() { return Math.max(0, ChronoUnit.DAYS.between(vencimento, dataDevolucao)); }
    }
    /** Resultado da consulta do Decorator (R09): SÓ id e título. Não tem estado, por isso o cache nunca autoriza reserva. */
    public record MetadadosLivro(String id, String titulo) { }
    /**
     * Facade (R04): as operações de circulação devolvem um COMPROVANTE (texto de resultado),
     * nunca as coleções internas de reservas/empréstimos.
     */
    public record Comprovante(String operacao, String detalhes) {
        @Override public String toString() { return operacao + ": " + detalhes; }
    }
    /** Adapter (R03): resposta institucional de uma cobrança, independente do banco. */
    public record Cobranca(String identificadorExterno, String banco) { }
    /** Adapter (R03): notificação de pagamento já TRADUZIDA para a situação institucional (PAGO / PENDENTE). */
    public record ConfirmacaoPagamento(String identificadorBoleto, String situacao) { }
    public record DadosMulta(BigDecimal valor, long diasAtraso, String politica) { }
}
