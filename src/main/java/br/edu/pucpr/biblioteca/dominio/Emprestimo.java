package br.edu.pucpr.biblioteca.dominio;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Empréstimo de um livro a um estudante (dados usados pelo State do R01 e pela multa).
 *
 * Guarda o vencimento e o contador de renovações. A regra "no máximo 2 renovações" fica aqui
 * porque numRenovacoes é dado do EMPRÉSTIMO; quem chama renovar() é a classe de estado
 * (EstadosLivro.Emprestado / Renovado), antes de transitar.
 */
public final class Emprestimo {
    private final String idLivro;
    private final String idEstudante;
    private LocalDate dataVencimento;
    private int numRenovacoes;

    /** Empréstimo novo: começa com zero renovações. */
    public Emprestimo(String idLivro, String idEstudante, LocalDate vencimento) {
        this(idLivro, idEstudante, vencimento, 0);
    }
    /** Usado também pelos repositórios para reconstituir um empréstimo gravado. Rejeita contador fora de 0..2. */
    public Emprestimo(String idLivro, String idEstudante, LocalDate vencimento, int renovacoes) {
        this.idLivro = Objects.requireNonNull(idLivro);
        this.idEstudante = Objects.requireNonNull(idEstudante);
        this.dataVencimento = Objects.requireNonNull(vencimento);
        this.numRenovacoes = renovacoes;
        if (renovacoes < 0 || renovacoes > 2) throw new IllegalArgumentException("Renovações inválidas");
    }
    /** Cópia defensiva para o repositório em memória (necessária para o rollback por snapshot). */
    public Emprestimo copia() { return new Emprestimo(idLivro, idEstudante, dataVencimento, numRenovacoes); }
    public String idLivro() { return idLivro; }
    public String idEstudante() { return idEstudante; }
    public LocalDate vencimento() { return dataVencimento; }
    public int numRenovacoes() { return numRenovacoes; }

    /**
     * GUARDA da renovação (premissa do Trabalho 02: numRenovacoes < 2).
     *
     * Pergunta da arguição: "por que a guarda é avaliada antes de alterar o vencimento?"
     *   -> se alterasse primeiro e checasse depois, uma terceira renovação recusada deixaria
     *      o vencimento já somado (mutação parcial). Checando antes, a exceção sai e NADA muda.
     * Cenário R01: a terceira renovação lança exceção e o vencimento continua o mesmo.
     */
    public void renovar() {
        if (numRenovacoes >= 2) throw new IllegalStateException("Terceira renovação recusada");
        dataVencimento = dataVencimento.plusDays(30);   // cada renovação acrescenta 30 dias
        numRenovacoes++;
    }
}
