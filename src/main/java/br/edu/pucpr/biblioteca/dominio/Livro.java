package br.edu.pucpr.biblioteca.dominio;

import br.edu.pucpr.biblioteca.dominio.estados.EstadosLivro;
import br.edu.pucpr.biblioteca.dominio.estados.EstadosLivro.Estado;
import java.util.Objects;

/**
 * R01 — STATE · papel: CONTEXT.
 *
 * O Livro guarda apenas o rótulo do estado atual ({@link Estado}) e DELEGA cada evento
 * ao objeto de estado correspondente. Ele nunca decide sozinho se uma operação é válida:
 * não existe nenhum if/switch sobre o estado aqui dentro.
 *
 * Como funciona a delegação (pergunta clássica da arguição):
 *   livro.reservar()
 *     -> EstadosLivro.para(estado)   // busca no mapa o objeto do estado atual (ex.: Livre)
 *     -> .reservar(this)             // o estado decide: aceita e chama transitar(...) ou lança exceção
 *
 * "E se eu tirar o State?" -> cada método teria um switch(estado) com as regras de todos os
 * estados misturadas; acrescentar um estado exigiria editar todos esses switches.
 *
 * Persistência: só o enum é gravado (coluna livros.estado). Ao ler, o enum é convertido de
 * volta (Estado.valueOf) e EstadosLivro.para(...) devolve o objeto de estado certo.
 */
public final class Livro {
    private final String id;
    private final String titulo;
    /** Rótulo do estado atual. O comportamento fica nas classes de EstadosLivro, não aqui. */
    private Estado estado;

    /** Todo livro novo nasce LIVRE (estado inicial do diagrama de estados). */
    public Livro(String id, String titulo) { this(id, titulo, Estado.LIVRE); }
    /** Construtor usado pelos repositórios para RECONSTITUIR um livro já gravado, com o estado salvo. */
    public Livro(String id, String titulo, Estado estado) {
        this.id=Objects.requireNonNull(id); this.titulo=Objects.requireNonNull(titulo);
        this.estado=Objects.requireNonNull(estado);
    }
    /** Cópia defensiva: o repositório em memória guarda cópias para que o rollback funcione. */
    public Livro copia() { return new Livro(id, titulo, estado); }
    public String id() { return id; }
    public String titulo() { return titulo; }
    public Estado estado() { return estado; }

    /**
     * Troca o estado. Só as classes de estado (EstadosLivro.*) devem chamar este método.
     * Pergunta provável: "por que é public?" -> Livro (pacote dominio) e os estados
     * (pacote dominio.estados) estão em pacotes diferentes, então package-private não alcançaria.
     */
    public void transitar(Estado novo) { this.estado = novo; } // Apenas as classes de estado devem invocar.

    // ---- Eventos da máquina de estados: cada um só repassa ao objeto do estado atual ----
    // Evento recusado pelo estado = IllegalStateException e NENHUM atributo do livro muda.
    public void reservar() { EstadosLivro.para(estado).reservar(this); }
    public void emprestar() { EstadosLivro.para(estado).emprestar(this); }
    /** A guarda numRenovacoes < 2 roda dentro de Emprestimo.renovar(), chamada pelo estado ANTES de transitar. */
    public void renovar(Emprestimo emprestimo) { EstadosLivro.para(estado).renovar(this, emprestimo); }
    public void devolver() { EstadosLivro.para(estado).devolver(this); }
    /** Só DEVOLVIDO aceita: bom estado -> LIVRE; danificado -> REPARANDO. */
    public void inspecionar(boolean bomEstado) { EstadosLivro.para(estado).inspecionar(this, bomEstado); }
    public void enviarParaReparo() { EstadosLivro.para(estado).enviarParaReparo(this); }
    public void retornarDoReparo() { EstadosLivro.para(estado).retornarDoReparo(this); }
    /** Evento de laudo irrecuperável: só REPARANDO aceita e leva a INCINERADO. */
    public void laudoIrrecuperavel() { EstadosLivro.para(estado).laudoIrrecuperavel(this); }
    /** Só LIVRE aceita; assim o TratadorDeAcervo não precisa de if sobre a situação do livro. */
    public void excluir() { EstadosLivro.para(estado).excluir(this); }
}
