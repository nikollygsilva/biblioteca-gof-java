package br.edu.pucpr.biblioteca.api;

import br.edu.pucpr.biblioteca.dominio.*;
import br.edu.pucpr.biblioteca.dominio.Entidades.*;

/**
 * Interfaces institucionais iLivro/iPessoa/iEstudante/iAtendente/iEmprestimo/iDevolucao dos UML.
 *
 * Preservadas do Trabalho 02 (exigência do R07 e do contexto comum). Mudança registrada no README:
 * as operações de circulação passaram a devolver Comprovante (antes void), por causa da Facade (R04).
 */
public final class Interfaces {
    private Interfaces() { }
    public interface iLivro {
        void inserirLivro(Livro livro);
        Livro consultarLivro(String id);
        void atualizarLivro(Livro livro);
        void excluirLivro(String id);
    }
    public interface iPessoa {
        void inserirPessoa(Pessoa pessoa);
        Pessoa consultarPessoa(String cpf);
        void atualizarPessoa(Pessoa pessoa);
        void excluirPessoa(String cpf);
    }
    public interface iEstudante {
        void inserirEstudante(Estudante estudante);
        Estudante consultarEstudante(String id);
        void atualizarEstudante(Estudante estudante);
        void excluirEstudante(String id);
    }
    public interface iAtendente {
        void inserirAtendente(Atendente atendente);
        Atendente consultarAtendente(String id);
        void atualizarAtendente(Atendente atendente);
        void excluirAtendente(String id);
    }
    public interface iEmprestimo {
        Comprovante solicitarReserva(String livro,String estudante);
        Reserva consultarReserva(String livro,String estudante);
        Comprovante emprestarLivro(String livro,String estudante);
        Comprovante renovarEmprestimo(String livro,String estudante);
    }
    public interface iMulta {
        Boleto emitir(Devolucao devolucao);
    }
    /** Base do TARGET do Adapter (R03): CobrancaInstitucional estende esta interface. */
    public interface iCobranca {
        Cobranca cobrar(Boleto boleto, String cpf);
        ConfirmacaoPagamento notificar(String identificadorBoleto, String situacaoExterna);
    }
    public interface iQuitacao {
        boolean confirmar(String identificadorBoleto, String situacaoExterna);
    }
    public interface iDevolucao {
        Comprovante registrarDevolucao(String livro,String estudante);
        Boleto emitirBoletoMulta(String idDevolucao);
        Comprovante inspecionarLivro(String livro,boolean bomEstado);
        Comprovante enviarParaReparo(String livro);
        Comprovante registrarRetornoReparo(String livro);
        Comprovante laudoIrrecuperavel(String livro);
    }
}
