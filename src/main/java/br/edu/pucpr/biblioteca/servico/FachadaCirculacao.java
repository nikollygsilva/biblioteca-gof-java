package br.edu.pucpr.biblioteca.servico;

import br.edu.pucpr.biblioteca.dominio.*;
import br.edu.pucpr.biblioteca.dominio.Entidades.*;
import br.edu.pucpr.biblioteca.persistencia.Contratos.UnidadeTrabalho;
import br.edu.pucpr.biblioteca.servico.Estruturas.*;
import br.edu.pucpr.biblioteca.servico.eventos.Devolucoes.*;
import java.time.*;
import java.util.UUID;

/**
 * R04 — FACADE · papel: FACADE.
 *
 * Entrada única para as operações de circulação (reservar, emprestar, renovar, devolver, inspecionar...).
 * SUBSISTEMAS coordenados: Acervo, Relacionamento, Movimentacao (pontos internos do Trabalho 02),
 * Livro (com seu State) e a UnidadeTrabalho (transação).
 * CLIENTES: TratadorDeEmprestimo, TratadorDeDevolucao e o Executor do Command (R06).
 *
 * Regras que o professor vai perguntar:
 *   - Cada operação inteira roda dentro de unidade.executar(...): ou TUDO é gravado, ou NADA (rollback).
 *   - O retorno é sempre um Comprovante, nunca uma coleção interna (a interface não manipula listas do domínio).
 *   - A interface (CLI) não acessa Movimentacao direto: se acessasse, teria que conhecer a ordem dos passos
 *     e a transação, e regra de domínio vazaria para a tela.
 *   - A validade de cada passo sobre o livro é decidida pelo STATE (l.reservar(), l.emprestar()...), não por if aqui.
 */
public final class FachadaCirculacao {
    private final Acervo acervo;
    private final Relacionamento relacionamento;
    private final Movimentacao movimentacao;
    private final UnidadeTrabalho unidade;
    /** Publicador do Observer (R05): a fachada avisa os ouvintes depois que a devolução é confirmada. */
    private final Publicador publicador;
    private final Clock clock;

    public FachadaCirculacao(Acervo acervo,Relacionamento relacionamento,Movimentacao movimentacao,
                            UnidadeTrabalho unidade,Publicador publicador,Clock clock) {
        this.acervo=acervo;this.relacionamento=relacionamento;this.movimentacao=movimentacao;
        this.unidade=unidade;this.publicador=publicador;this.clock=clock;
    }

    /**
     * RESERVA (chamada pelo comando Comandos.Reserva).
     * Ordem: estudante existe? -> busca livro -> State decide (só LIVRE aceita) -> grava reserva -> grava livro.
     */
    public Comprovante reservar(String livro,String estudante) {
        return unidade.executar(()->{
            relacionamento.exigirEstudante(estudante);
            Livro l=acervo.exigir(livro);       // lê o estado ATUAL do repositório (nunca do cache do R09)
            l.reservar(); // State decide se é válido, não um if no tratador.
            movimentacao.adicionarReserva(new Reserva(livro,estudante,clock.instant()));
            acervo.atualizarLivro(l);
            return new Comprovante("RESERVA","Livro "+livro+" para "+estudante);
        });
    }

    /**
     * EFETIVAÇÃO DO EMPRÉSTIMO — é o cenário de verificação do R04.
     * Ordem dos passos (dentro de UMA transação):
     *   1. valida que existe reserva deste estudante para este livro;
     *   2. busca o Livro no Acervo;
     *   3. State: l.emprestar() (só RESERVADO aceita);
     *   4. remove a reserva ativa;
     *   5. cria o empréstimo com vencimento em 30 dias e grava;
     *   6. grava o Livro com o novo estado;
     *   7. devolve um Comprovante.
     * "O que é desfeito se um passo falhar no meio?" -> TUDO. Ex.: se o passo 5 falhar, a reserva removida
     * no passo 4 volta, porque a unidade de trabalho descarta a transação inteira.
     * Teste: R04_falhaNoMeioDaFachadaReverteReservaEEmprestimo (força erro em salvarEmprestimo).
     */
    public Comprovante emprestar(String livro,String estudante) {
        return unidade.executar(()->{
            Reserva r=movimentacao.consultarReserva(livro,estudante);                       // 1.
            if(r==null)throw new IllegalStateException("Reserva inexistente para esse estudante");
            Livro l=acervo.exigir(livro);                                                   // 2.
            l.emprestar();                                                                  // 3. State
            movimentacao.removerReserva(livro);                                             // 4.
            Emprestimo e=new Emprestimo(livro,estudante,LocalDate.now(clock).plusDays(30)); // 5.
            movimentacao.adicionarEmprestimo(e);
            acervo.atualizarLivro(l);                                                       // 6.
            return new Comprovante("EMPRÉSTIMO","Livro "+livro+" vence em "+e.vencimento()); // 7.
        });
    }

    /**
     * RENOVAÇÃO (chamada pelo comando Comandos.Renovacao).
     * O State do livro chama Emprestimo.renovar(), onde está a guarda numRenovacoes < 2.
     * Se a guarda recusar, a exceção desfaz a transação e o vencimento não muda.
     */
    public Comprovante renovar(String livro,String estudante) {
        return unidade.executar(()->{
            Emprestimo e=movimentacao.consultarEmprestimo(livro,estudante);
            if(e==null)throw new IllegalStateException("Empréstimo inexistente");
            Livro l=acervo.exigir(livro);
            l.renovar(e);                        // State + guarda
            movimentacao.adicionarEmprestimo(e); // grava o vencimento novo
            acervo.atualizarLivro(l);
            return new Comprovante("RENOVAÇÃO","Renovações "+e.numRenovacoes()+", vence em "+e.vencimento());
        });
    }
    public Comprovante devolver(String livro,String estudante) {return devolverEm(livro,estudante,LocalDate.now(clock));}

    /**
     * DEVOLUÇÃO. Data explícita usada pelos testes e pela demonstração de atraso.
     * Duas fases — ponto central do Observer (R05):
     *   FASE 1 (transação): State devolve (-> DEVOLVIDO), grava a devolução, remove o empréstimo, grava o livro.
     *   FASE 2 (depois do commit): publica o evento para os ouvintes (inspeção, multa, indicadores, e-mail).
     * Por que publicar só depois? Se publicasse antes e a devolução falhasse, haveria multa/e-mail de uma
     * devolução que não aconteceu. E se um ouvinte falhar, a devolução já está confirmada e não é desfeita.
     * O livro fica DEVOLVIDO até a inspeção decidir.
     */
    public Comprovante devolverEm(String livro,String estudante,LocalDate data) {
        Devolucao d=unidade.executar(()->{                                       // FASE 1
            Emprestimo e=movimentacao.consultarEmprestimo(livro,estudante);
            if(e==null)throw new IllegalStateException("Empréstimo inexistente");
            Livro l=acervo.exigir(livro);
            l.devolver();
            Devolucao retorno=new Devolucao(UUID.randomUUID().toString(),livro,estudante,e.vencimento(),data);
            movimentacao.adicionarDevolucao(retorno);
            movimentacao.removerEmprestimo(livro);
            acervo.atualizarLivro(l);
            return retorno;
        });
        publicador.publicar(new Evento(d)); // somente depois do COMMIT          // FASE 2
        return new Comprovante("DEVOLUÇÃO","Livro "+livro+", atraso "+d.diasAtraso()+" dia(s); aguardando inspeção; devolução "+d.id());
    }

    // ---- Eventos de manutenção do livro: todos seguem o mesmo molde (buscar -> State -> gravar) ----
    /** Inspeção: só DEVOLVIDO aceita. Bom -> LIVRE, ruim -> REPARANDO. */
    public Comprovante inspecionar(String id,boolean bom) {
        return atualizarEstado(id,l->l.inspecionar(bom),"INSPEÇÃO");
    }
    public Comprovante enviarParaReparo(String id) {return atualizarEstado(id,Livro::enviarParaReparo,"REPARO");}
    public Comprovante retornarDoReparo(String id){return atualizarEstado(id,Livro::retornarDoReparo,"RETORNO REPARO");}
    /** Laudo irrecuperável: REPARANDO -> INCINERADO. */
    public Comprovante laudoIrrecuperavel(String id){return atualizarEstado(id,Livro::laudoIrrecuperavel,"INCINERAÇÃO");}
    /** Molde comum: o evento recebido como função é aplicado ao livro; o State aceita ou recusa. */
    private Comprovante atualizarEstado(String id,java.util.function.Consumer<Livro> evento,String tipo) {
        return unidade.executar(()->{
            Livro l=acervo.exigir(id); evento.accept(l);acervo.atualizarLivro(l);
            return new Comprovante(tipo,"Livro "+id+" -> "+l.estado());
        });
    }
}
