package br.edu.pucpr.biblioteca.servico;

import br.edu.pucpr.biblioteca.dominio.*;
import br.edu.pucpr.biblioteca.dominio.Entidades.*;
import br.edu.pucpr.biblioteca.persistencia.Contratos.*;

/**
 * Pontos internos de acesso preservados conforme diagramas: Acervo, Relacionamento, Movimentacao.
 *
 * São os três pontos de acesso do Trabalho 02, mantidos como o enunciado exige. Cada um esconde um
 * repositório criado pela Abstract Factory (R08), então funcionam igual em memória e no MySQL.
 * Quem os coordena em operações completas é a FachadaCirculacao (R04); a interface nunca os acessa.
 */
public final class Estruturas {
    private Estruturas() { }

    /** ACERVO: livros. exigir(...) = consulta que lança exceção se o livro não existe. */
    public static final class Acervo {
        private final RepositorioLivro repo;
        public Acervo(RepositorioLivro r){repo=r;}
        public Livro consultarLivro(String id){return repo.buscar(id);}
        public void inserirLivro(Livro l){repo.salvar(l);}
        public void atualizarLivro(Livro l){repo.salvar(l);}
        public void excluirLivro(String id){repo.excluir(id);}
        public java.util.List<Livro> listarLivros(){return repo.listar();}
        public Livro exigir(String id){Livro l=consultarLivro(id);if(l==null)throw new IllegalArgumentException("Livro não encontrado: "+id);return l;}
    }
    /** RELACIONAMENTO: pessoas, estudantes e atendentes. */
    public static final class Relacionamento {
        private final RepositorioRelacionamento repo;
        public Relacionamento(RepositorioRelacionamento r){repo=r;}
        public Pessoa consultarPessoa(String id){return repo.pessoa(id);}
        public void inserirPessoa(Pessoa p){repo.salvarPessoa(p);}
        public void excluirPessoa(String id){repo.excluirPessoa(id);}
        public Estudante consultarEstudante(String id){return repo.estudante(id);}
        public void inserirEstudante(Estudante e){repo.salvarEstudante(e);}
        public void excluirEstudante(String id){repo.excluirEstudante(id);}
        public Atendente consultarAtendente(String id){return repo.atendente(id);}
        public void inserirAtendente(Atendente a){repo.salvarAtendente(a);}
        public void excluirAtendente(String id){repo.excluirAtendente(id);}
        public Estudante exigirEstudante(String id){Estudante e=consultarEstudante(id);if(e==null)throw new IllegalArgumentException("Estudante não encontrado: "+id);return e;}
    }
    /**
     * MOVIMENTACAO: reservas, empréstimos, devoluções, boletos e o registro de comandos executados
     * (usado pelo Executor do Command para a idempotência).
     */
    public static final class Movimentacao {
        private final RepositorioMovimentacao repo;
        public Movimentacao(RepositorioMovimentacao r){repo=r;}
        public Reserva consultarReserva(String l,String e){return repo.reserva(l,e);}
        public void adicionarReserva(Reserva r){repo.salvarReserva(r);}
        public void removerReserva(String idLivro){repo.excluirReserva(idLivro);}
        public Emprestimo consultarEmprestimo(String l,String e){return repo.emprestimo(l,e);}
        public void adicionarEmprestimo(Emprestimo e){repo.salvarEmprestimo(e);}
        public void removerEmprestimo(String l){repo.excluirEmprestimo(l);}
        public void adicionarDevolucao(Devolucao d){repo.salvarDevolucao(d);}
        public Devolucao consultarDevolucao(String id){return repo.devolucao(id);}
        public Devolucao consultarUltimaDevolucao(String livro,String estudante){return repo.ultimaDevolucao(livro,estudante);}
        public Boleto consultarBoleto(String id){return repo.boleto(id);}
        public Boleto boletoPorDevolucao(String id){return repo.boletoPorDevolucao(id);}
        public void salvarBoleto(Boleto b){repo.salvarBoleto(b);}
        /** Devolve false se o id do comando já foi registrado (inclusive em chamadas simultâneas). */
        public boolean registrarComando(String id,String autor,java.time.Instant instante,String tipo){return repo.registrarComando(id,autor,instante,tipo);}
    }
}
