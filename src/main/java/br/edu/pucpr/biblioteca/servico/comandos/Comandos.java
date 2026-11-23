package br.edu.pucpr.biblioteca.servico.comandos;

import br.edu.pucpr.biblioteca.dominio.Entidades.Comprovante;
import br.edu.pucpr.biblioteca.persistencia.Contratos.UnidadeTrabalho;
import br.edu.pucpr.biblioteca.servico.Estruturas.Movimentacao;
import br.edu.pucpr.biblioteca.servico.FachadaCirculacao;
import java.time.Instant;
import java.util.UUID;

/**
 * R06 — COMMAND · papéis:
 *   COMMAND           = {@link Comando}
 *   CONCRETE COMMANDS = {@link Reserva} e {@link Renovacao}
 *   INVOKER           = {@link Executor}
 *   RECEIVER          = FachadaCirculacao (quem faz o trabalho de verdade)
 *   CLIENT            = TratadorDeEmprestimo (cria o comando a pedido da tela)
 *
 * "Quem cria, quem invoca e quem executa?"
 *   cria   -> TratadorDeEmprestimo: new Renovacao(UUID.randomUUID(), autor, agora, livro, estudante)
 *   invoca -> Executor.submeter(comando)
 *   executa-> comando.executar(fachada) chama fachada.renovar(...); as REGRAS continuam no domínio
 *             (fachada + State + guarda do Emprestimo), não dentro do comando.
 *
 * "Por que não há desfazer (undo)?" -> reserva e renovação são fatos de negócio; reverter seria outra
 * operação com regras próprias (ex.: cancelar reserva), não um undo técnico que passaria por cima do State.
 */
public final class Comandos {
    private Comandos() { }

    /** COMMAND: solicitação como objeto — identificador (idempotência), autor, instante de criação e execução. */
    public interface Comando {
        UUID id(); String autor(); Instant criadoEm(); String tipo();
        Comprovante executar(FachadaCirculacao fachada);
    }
    /** CONCRETE COMMAND de reserva. O construtor compacto do record valida os metadados obrigatórios. */
    public record Reserva(UUID id,String autor,Instant criadoEm,String livro,String estudante) implements Comando {
        public Reserva { verificar(id,autor,criadoEm); }
        public String tipo() {return "RESERVA";}
        public Comprovante executar(FachadaCirculacao fachada) {return fachada.reservar(livro,estudante);}
    }
    /** CONCRETE COMMAND de renovação. Cada execução bem-sucedida soma 30 dias (limite de 2 fica no domínio). */
    public record Renovacao(UUID id,String autor,Instant criadoEm,String livro,String estudante) implements Comando {
        public Renovacao { verificar(id,autor,criadoEm); }
        public String tipo() {return "RENOVACAO";}
        public Comprovante executar(FachadaCirculacao fachada) {return fachada.renovar(livro,estudante);}
    }
    /** Comando sem id, autor ou instante é rejeitado na criação. */
    private static void verificar(UUID id,String autor,Instant criacao) {
        if(id==null || autor==null || autor.isBlank() || criacao==null)throw new IllegalArgumentException("Comando incompleto");
    }

    /**
     * INVOKER: garante que o MESMO identificador não seja executado duas vezes.
     *
     * Pergunta: "como a repetição é detectada mesmo com duas submissões simultâneas?"
     *   -> registrarComando(id) é ATÔMICO e devolve false se o id já existe:
     *        em memória: a transação é synchronized (uma por vez) e o registro é Set.add (false se repetido);
     *        no MySQL:   INSERT IGNORE com o id como chave primária — o banco aceita só um.
     *   -> quem chega em segundo recebe Comprovante "IGNORADO" e nada é executado.
     * Detalhe: registro e execução estão na MESMA transação. Se a execução falhar (ex.: 3ª renovação),
     * o registro do id também é desfeito.
     * Teste: R06_renovacaoSimultaneaMesmoUuidAcrescentaSoTrintaDias (2 threads, mesmo UUID, só +30 dias).
     * Duplicidade (mesmo UUID) é responsabilidade do Executor; limite de 2 renovações é regra do domínio.
     */
    public static final class Executor {
        private final UnidadeTrabalho unidade;
        private final Movimentacao movimentacao;
        private final FachadaCirculacao fachada;
        public Executor(UnidadeTrabalho unidade,Movimentacao movimentacao,FachadaCirculacao fachada) {
            this.unidade=unidade;this.movimentacao=movimentacao;this.fachada=fachada;
        }
        public Comprovante submeter(Comando comando) {
            return unidade.executar(()->{
                boolean primeiraVez=movimentacao.registrarComando(comando.id().toString(),comando.autor(),comando.criadoEm(),comando.tipo());
                if(!primeiraVez)return new Comprovante("IGNORADO","Comando "+comando.id()+" já executado");
                return comando.executar(fachada);   // a fachada participa da mesma transação (operação aninhada)
            });
        }
    }
}
