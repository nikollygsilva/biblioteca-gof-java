package br.edu.pucpr.biblioteca.dominio.estados;

import br.edu.pucpr.biblioteca.dominio.Emprestimo;
import br.edu.pucpr.biblioteca.dominio.Livro;
import java.util.Map;

/**
 * R01 — STATE · papéis: STATE (interface {@link Situacao}) e CONCRETE STATES (as 7 classes abaixo).
 *
 * Uma classe por situação do diagrama de estados (docs/uml/estado_livro.puml).
 * Regra de alinhamento com o modelo: cada SETA da máquina de estados é um MÉTODO
 * sobrescrito na classe do estado de ORIGEM; a guarda e a ação ficam no corpo desse método.
 *
 *   LIVRE      --reservar-->          RESERVADO
 *   LIVRE      --enviarParaReparo-->  REPARANDO
 *   LIVRE      --excluir-->           [removido do acervo]
 *   RESERVADO  --emprestar-->         EMPRESTADO
 *   EMPRESTADO --renovar [n<2]-->     RENOVADO
 *   RENOVADO   --renovar [n<2]-->     RENOVADO
 *   EMPRESTADO/RENOVADO --devolver--> DEVOLVIDO
 *   DEVOLVIDO  --inspecionar(bom)-->  LIVRE | (ruim) REPARANDO
 *   REPARANDO  --retornarDoReparo-->  LIVRE
 *   REPARANDO  --laudoIrrecuperavel-> INCINERADO (terminal)
 */
public final class EstadosLivro {
    private EstadosLivro() { }

    /** Só o RÓTULO do estado: é o que vai para o banco (coluna livros.estado). O comportamento está nas classes. */
    public enum Estado { LIVRE, RESERVADO, EMPRESTADO, RENOVADO, DEVOLVIDO, REPARANDO, INCINERADO }

    /**
     * STATE: declara TODOS os eventos do livro.
     *
     * Cada evento tem uma implementação default que RECUSA (lança IllegalStateException).
     * Cada estado concreto sobrescreve apenas os eventos que aceita.
     * Pergunta da arguição: "o que acontece quando um evento chega a um estado que não o aceita?"
     *   -> cai no default, chama negar(...), a exceção sobe ANTES de qualquer transitar(...);
     *      o livro não muda e, como tudo roda dentro de unidade.executar(...), nada é gravado.
     */
    public interface Situacao {
        default void reservar(Livro l) { negar("reservar", l); }
        default void emprestar(Livro l) { negar("emprestar", l); }
        default void renovar(Livro l, Emprestimo e) { negar("renovar", l); }
        default void devolver(Livro l) { negar("devolver", l); }
        default void inspecionar(Livro l, boolean bomEstado) { negar("inspecionar", l); }
        default void enviarParaReparo(Livro l) { negar("reparar", l); }
        default void retornarDoReparo(Livro l) { negar("retornar reparo", l); }
        default void laudoIrrecuperavel(Livro l) { negar("incinerar", l); }
        default void excluir(Livro l) { negar("excluir", l); }
        /** Recusa padrão: mensagem com a ação e o estado atual (aparece na CLI como "Operação recusada"). */
        private void negar(String acao, Livro l) { throw new IllegalStateException("Não é possível " + acao + " no estado " + l.estado()); }
    }

    /** LIVRE: aceita reservar, enviar para reparo e excluir. */
    public static final class Livre implements Situacao {
        @Override public void reservar(Livro l) { l.transitar(Estado.RESERVADO); }
        @Override public void enviarParaReparo(Livro l) { l.transitar(Estado.REPARANDO); }
        /** Só um livro LIVRE pode sair do acervo; o estado não muda, o tratador remove em seguida. */
        @Override public void excluir(Livro l) { }
    }
    /** RESERVADO: aceita apenas emprestar (a fachada já confirmou que a reserva é do estudante). */
    public static final class Reservado implements Situacao {
        @Override public void emprestar(Livro l) { l.transitar(Estado.EMPRESTADO); }
    }
    /**
     * EMPRESTADO: aceita renovar e devolver.
     * Ordem em renovar: PRIMEIRO e.renovar() (onde está a guarda numRenovacoes < 2 e a soma de 30 dias),
     * DEPOIS transitar. Se a guarda recusar, a exceção sai antes de transitar: estado e vencimento intactos.
     */
    public static final class Emprestado implements Situacao {
        @Override public void renovar(Livro l, Emprestimo e) { e.renovar(); l.transitar(Estado.RENOVADO); }
        @Override public void devolver(Livro l) { l.transitar(Estado.DEVOLVIDO); }
    }
    /** RENOVADO: pode renovar de novo (auto-transição) enquanto a guarda permitir, e pode devolver. */
    public static final class Renovado implements Situacao {
        @Override public void renovar(Livro l, Emprestimo e) { e.renovar(); l.transitar(Estado.RENOVADO); }
        @Override public void devolver(Livro l) { l.transitar(Estado.DEVOLVIDO); }
    }
    /**
     * DEVOLVIDO: livro voltou mas ainda NÃO foi inspecionado.
     * Não sobrescreve reservar -> reservar aqui é recusado (cenário de verificação do R01).
     * Só a inspeção decide o próximo estado.
     */
    public static final class Devolvido implements Situacao {
        @Override public void inspecionar(Livro l, boolean bom) { l.transitar(bom ? Estado.LIVRE : Estado.REPARANDO); }
    }
    /** REPARANDO: volta para LIVRE após o reparo, ou vai para INCINERADO com laudo irrecuperável. */
    public static final class Reparando implements Situacao {
        @Override public void retornarDoReparo(Livro l) { l.transitar(Estado.LIVRE); }
        @Override public void laudoIrrecuperavel(Livro l) { l.transitar(Estado.INCINERADO); }
    }
    /** INCINERADO: estado terminal. Não sobrescreve nada, então recusa todos os eventos. */
    public static final class Incinerado implements Situacao { }

    /**
     * Uma instância de cada estado, compartilhada por todos os livros.
     * Pode ser compartilhada porque os estados NÃO guardam dados: os dados ficam em Livro e Emprestimo.
     * Para acrescentar um estado: valor no enum + classe nova + entrada neste mapa + transições nos estados de origem.
     * Livro e tratadores não mudam.
     */
    private static final Map<Estado, Situacao> INSTANCIAS = Map.of(
            Estado.LIVRE,new Livre(), Estado.RESERVADO,new Reservado(),
            Estado.EMPRESTADO,new Emprestado(), Estado.RENOVADO,new Renovado(),
            Estado.DEVOLVIDO,new Devolvido(), Estado.REPARANDO,new Reparando(),
            Estado.INCINERADO,new Incinerado());
    /** Converte o rótulo (enum) no objeto de estado. É também como o estado é RECONSTITUÍDO após ler do banco. */
    public static Situacao para(Estado estado) { return INSTANCIAS.get(estado); }
}
