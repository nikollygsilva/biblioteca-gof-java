package br.edu.pucpr.biblioteca.persistencia;

import br.edu.pucpr.biblioteca.dominio.*;
import br.edu.pucpr.biblioteca.dominio.Entidades.*;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/**
 * R08 — ABSTRACT FACTORY · papéis:
 *   ABSTRACT FACTORY  = {@link FabricaPersistencia}
 *   ABSTRACT PRODUCTS = {@link UnidadeTrabalho}, {@link RepositorioLivro}, {@link RepositorioRelacionamento},
 *                       {@link RepositorioMovimentacao}
 *   CONCRETE FACTORIES = FabricaMemoria (testes) e FabricaMysql (produção)
 *
 * Interfaces segregadas: uma fábrica fornece uma única família coerente de repositórios.
 * O domínio e os serviços só conhecem estas interfaces; nunca sabem se estão em memória ou no MySQL.
 */
public final class Contratos {
    private Contratos() { }

    /**
     * Unidade de trabalho = TRANSAÇÃO. Tudo que roda dentro de executar(...) é confirmado junto
     * (commit) ou desfeito junto (rollback) se alguma exceção escapar. É o que dá atomicidade à Facade (R04).
     * Chamadas aninhadas (ex.: Executor -> Fachada) participam da transação que já está aberta.
     */
    public interface UnidadeTrabalho {
        <T> T executar(Supplier<T> bloco);
        default void executar(Runnable bloco) { executar(() -> { bloco.run(); return null; }); }
    }
    /** Repositório de livros (usado pelo Acervo). */
    public interface RepositorioLivro {
        Livro buscar(String id);
        void salvar(Livro livro);
        void excluir(String id);
        List<Livro> listar();
    }
    /** Repositório de pessoas, estudantes e atendentes (usado pelo Relacionamento). */
    public interface RepositorioRelacionamento {
        Pessoa pessoa(String cpf); void salvarPessoa(Pessoa pessoa); void excluirPessoa(String cpf);
        Estudante estudante(String id); void salvarEstudante(Estudante estudante); void excluirEstudante(String id);
        Atendente atendente(String id); void salvarAtendente(Atendente atendente); void excluirAtendente(String id);
    }
    /** Repositório de reservas, empréstimos, devoluções, boletos e comandos (usado pela Movimentacao). */
    public interface RepositorioMovimentacao {
        Reserva reserva(String livro, String estudante); void salvarReserva(Reserva r); void excluirReserva(String livro);
        Emprestimo emprestimo(String livro, String estudante); void salvarEmprestimo(Emprestimo e); void excluirEmprestimo(String livro);
        void salvarDevolucao(Devolucao d); Devolucao devolucao(String id); Devolucao ultimaDevolucao(String livro,String estudante);
        void salvarBoleto(Boleto boleto); Boleto boleto(String id); Boleto boletoPorDevolucao(String idDevolucao);
        /** Retorna false para o identificador já executado, inclusive com chamadas concorrentes. */
        boolean registrarComando(String id, String autor, Instant criadoEm, String tipo);
    }
    /**
     * ABSTRACT FACTORY: cria a unidade de trabalho e os três repositórios de UMA família.
     * Os produtos de uma mesma fábrica compartilham a mesma transação.
     */
    public interface FabricaPersistencia {
        UnidadeTrabalho unidadeTrabalho();
        RepositorioLivro livros();
        RepositorioRelacionamento relacionamento();
        RepositorioMovimentacao movimentacao();
    }
}
