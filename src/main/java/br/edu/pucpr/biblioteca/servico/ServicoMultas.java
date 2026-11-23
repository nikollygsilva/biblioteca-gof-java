package br.edu.pucpr.biblioteca.servico;

import br.edu.pucpr.biblioteca.dominio.*;
import br.edu.pucpr.biblioteca.dominio.Entidades.*;
import br.edu.pucpr.biblioteca.persistencia.Contratos.UnidadeTrabalho;
import br.edu.pucpr.biblioteca.servico.Estruturas.*;
import br.edu.pucpr.biblioteca.servico.banco.Bancos.CobrancaInstitucional;
import br.edu.pucpr.biblioteca.servico.estrategia.PoliticasMulta.Politica;
import java.time.*;
import java.util.UUID;

/**
 * Serviço de multas. Participa de DOIS padrões:
 *   R02 — STRATEGY · papel: CONTEXT  -> usa uma {@link Politica} para calcular o valor.
 *   R03 — ADAPTER  · papel: CLIENT   -> cobra por uma {@link CobrancaInstitucional} (o alvo do Adapter).
 *
 * O que NÃO existe aqui (e é isso que o professor quer ver):
 *   - nenhum if por política (o cálculo é politica.calcular(...));
 *   - nenhum if por banco (a cobrança é banco.cobrar(...); centavos/CPF ficam nos adaptadores);
 *   - nenhum recálculo de boleto antigo.
 * A política e o banco chegam prontos pelo construtor (injeção feita no SistemaBiblioteca / Main).
 */
public final class ServicoMultas implements br.edu.pucpr.biblioteca.api.Interfaces.iMulta, br.edu.pucpr.biblioteca.api.Interfaces.iQuitacao {
    private final UnidadeTrabalho unidade;
    private final Movimentacao movimentacao;
    private final Relacionamento relacionamento;
    /** Adapter escolhido pela instalação (AdaptadorBancoA ou AdaptadorBancoB). O serviço só conhece a interface. */
    private final CobrancaInstitucional banco;
    /** Strategy vigente. Não é final porque a configuração administrativa pode trocá-la (definirPolitica). */
    private Politica politica;
    /** Relógio injetado: permite testes com data fixa. */
    private final Clock clock;

    public ServicoMultas(UnidadeTrabalho unidade,Movimentacao movimentacao,Relacionamento relacionamento,
                        CobrancaInstitucional banco,Politica politica,Clock clock) {
        this.unidade=unidade;this.movimentacao=movimentacao;this.relacionamento=relacionamento;
        this.banco=banco;this.politica=politica;this.clock=clock;
    }
    /** Troca a política vigente. Vale só para as PRÓXIMAS emissões; boletos emitidos guardam a política antiga. */
    public void definirPolitica(Politica politica) { this.politica=politica; }
    public Politica politica() { return politica; }

    /**
     * Emite o boleto de multa de uma devolução. Roda numa transação (unidade.executar).
     * Passo a passo:
     *   1. sem atraso -> não há multa (retorna null);
     *   2. já existe boleto para esta devolução -> devolve o mesmo (não emite duas vezes nem recalcula);
     *   3. busca estudante e pessoa (para o CPF);
     *   4. calcula com a Strategy e CONGELA no boleto o valor e o nome da política;
     *   5. cobra pelo Adapter do banco e guarda o identificador externo;
     *   6. grava o boleto.
     * Chamado pelo ouvinte EmissorMulta (Observer) e pela opção manual de emissão.
     */
    public Boleto emitir(Devolucao devolucao) {
        return unidade.executar(()->{
            if(devolucao.diasAtraso()==0) return null;                              // 1. devolução no prazo
            Boleto anterior=movimentacao.boletoPorDevolucao(devolucao.id());
            if(anterior!=null)return anterior;                                      // 2. idempotente
            Estudante estudante=relacionamento.exigirEstudante(devolucao.idEstudante());
            Pessoa pessoa=relacionamento.consultarPessoa(estudante.cpf());          // 3.
            Boleto b=new Boleto(UUID.randomUUID().toString(),devolucao.id(),estudante.id(),politica.nome(),
                    politica.calcular(devolucao.diasAtraso()),devolucao.diasAtraso(),LocalDate.now(clock),false,null); // 4. Strategy
            Cobranca c=banco.cobrar(b,pessoa.cpf());                                // 5. Adapter: sem if por banco
            b.registrarCobranca(c.identificadorExterno());
            movimentacao.salvarBoleto(b);                                           // 6.
            return b;
        });
    }
    public Boleto consultar(String id) { return unidade.executar(()->movimentacao.consultarBoleto(id)); }

    /**
     * Confirma pagamento a partir da notificação do banco (R03).
     * 1. O ADAPTADOR traduz o código do banco ("00" no A, "LIQUIDADO" no B) para PAGO/PENDENTE.
     * 2. Dentro da transação: se não é PAGO, ou se o boleto JÁ está quitado, retorna false sem gravar.
     * Pergunta: "como a quitação é protegida contra notificações repetidas?" -> pelo teste b.quitado():
     * a segunda notificação igual encontra o boleto quitado e não faz nada (idempotência).
     */
    public boolean confirmar(String id,String situacaoExterna) {
        ConfirmacaoPagamento noticia=banco.notificar(id,situacaoExterna);          // 1. tradução no Adapter
        return unidade.executar(()->{
            Boleto b=movimentacao.consultarBoleto(noticia.identificadorBoleto());
            if(b==null)throw new IllegalArgumentException("Boleto inexistente");
            if(!"PAGO".equals(noticia.situacao()) || b.quitado())return false;     // 2. pendente ou repetida
            b.quitar(); movimentacao.salvarBoleto(b);
            return true;
        });
    }
}
