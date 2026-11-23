package br.edu.pucpr.biblioteca.servico;

import br.edu.pucpr.biblioteca.api.Tratadores.*;
import br.edu.pucpr.biblioteca.persistencia.Contratos.*;
import br.edu.pucpr.biblioteca.persistencia.memoria.FabricaMemoria;
import br.edu.pucpr.biblioteca.persistencia.mysql.FabricaMysql;
import br.edu.pucpr.biblioteca.servico.Estruturas.*;
import br.edu.pucpr.biblioteca.servico.banco.Bancos.*;
import br.edu.pucpr.biblioteca.servico.consulta.Consultas.*;
import br.edu.pucpr.biblioteca.servico.estrategia.PoliticasMulta.*;
import br.edu.pucpr.biblioteca.servico.eventos.Devolucoes.*;
import java.time.Clock;
import java.util.Locale;

/**
 * Composition Root: a família é escolhida UMA ÚNICA VEZ no bootstrap.
 *
 * É aqui que TODOS os padrões são ligados entre si. Mapa do que cada trecho do construtor faz:
 *   - R08 Abstract Factory: recebe UMA fábrica e tira dela a unidade de trabalho e os 3 repositórios;
 *   - pontos internos: cria Acervo, Relacionamento e Movimentacao sobre esses repositórios;
 *   - R02 Strategy + R03 Adapter: injeta a política e o banco no ServicoMultas;
 *   - R05 Observer: registra os 4 ouvintes no publicador (é aqui que entra um ouvinte novo);
 *   - R04 Facade: cria a FachadaCirculacao;
 *   - R06 Command: o TratadorDeEmprestimo cria o Executor internamente;
 *   - R09 Decorator: monta a cadeia Metrica -> Cache -> Original.
 * A CLI só recebe os tratadores (getters no fim da classe).
 */
public final class SistemaBiblioteca {
    private final FabricaPersistencia fabrica;
    private final Publicador publicador = new Publicador();
    private final FilaInspecao inspecao = new FilaInspecao();
    private final Indicadores indicadores = new Indicadores();
    private final AvisoEmail avisoEmail = new AvisoEmail();
    private final ServicoMultas multas;
    private final TratadorDeAcervo livros;
    private final TratadorDePessoa pessoas;
    private final TratadorDeEstudante estudantes;
    private final TratadorDeAtendente atendentes;
    private final TratadorDeEmprestimo emprestimos;
    private final TratadorDeDevolucao devolucoes;
    private final TratadorFinanceiro financeiro;
    private final TratadorDeConsulta consultas;
    private final TratadorDeHierarquia hierarquia;
    private final Metrica metrica;
    private final Cache cache;

    /**
     * R08: ESCOLHA DA FAMÍLIA pelo perfil de execução (variável BIBLIOTECA_PERFIL no Main).
     * "Onde a família é escolhida?" -> aqui, uma única vez. memoria/teste = FabricaMemoria;
     * mysql/producao = FabricaMysql. Depois disso o sistema só enxerga a interface FabricaPersistencia.
     */
    public static FabricaPersistencia criarFamilia(String perfil,String jdbcUrl,String usuario,String senha) {
        return switch(perfil.toLowerCase(Locale.ROOT)) {
            case "memoria", "teste" -> new FabricaMemoria();
            case "mysql", "producao" -> new FabricaMysql(jdbcUrl,usuario,senha);
            default -> throw new IllegalArgumentException("Perfil inválido: "+perfil);
        };
    }

    public SistemaBiblioteca(FabricaPersistencia fabrica,Clock clock,CobrancaInstitucional banco,Politica politica) {
        this.fabrica=fabrica;
        // R08: tudo vem da MESMA fábrica -> impossível misturar repositório em memória com relacional
        UnidadeTrabalho unidade=fabrica.unidadeTrabalho();
        Acervo a=new Acervo(fabrica.livros());
        Relacionamento r=new Relacionamento(fabrica.relacionamento());
        Movimentacao m=new Movimentacao(fabrica.movimentacao());
        // R02 + R03: política (Strategy) e banco (Adapter) chegam prontos; o serviço não escolhe nada
        multas=new ServicoMultas(unidade,m,r,banco,politica,clock);
        // R05: registro dos ouvintes da devolução. ALTERAÇÃO AO VIVO: publicador.adicionar(new NovoOuvinte());
        publicador.adicionar(inspecao);
        publicador.adicionar(new EmissorMulta(multas));
        publicador.adicionar(indicadores);
        publicador.adicionar(avisoEmail);
        // R04: a fachada recebe os três pontos internos, a transação e o publicador
        FachadaCirculacao fachada=new FachadaCirculacao(a,r,m,unidade,publicador,clock);
        // Tratadores: a única porta usada pela interface
        livros=new TratadorDeAcervo(unidade,a);
        hierarquia=new TratadorDeHierarquia(livros);
        pessoas=new TratadorDePessoa(unidade,r);
        estudantes=new TratadorDeEstudante(unidade,r);
        atendentes=new TratadorDeAtendente(unidade,r);
        emprestimos=new TratadorDeEmprestimo(unidade,m,fachada,clock);   // cria o Executor do Command (R06)
        devolucoes=new TratadorDeDevolucao(fachada,m,multas,unidade);
        financeiro=new TratadorFinanceiro(multas);
        // R09: cadeia de decoradores. A ORDEM importa: Metrica por fora conta até os acertos de cache
        ConsultaAcervo consulta=new br.edu.pucpr.biblioteca.servico.consulta.Consultas.Original(a,unidade);
        cache=new Cache(consulta);
        metrica=new Metrica(cache); // Medição por fora: conta até os acertos de cache.
        consultas=new TratadorDeConsulta(metrica);
    }

    /** Montagem usada pelos testes: família em memória, banco A, política original e relógio fixo. */
    public static SistemaBiblioteca paraTestes(Clock clock) {
        return new SistemaBiblioteca(new FabricaMemoria(),clock,new AdaptadorBancoA(new ApiBancoA()),new br.edu.pucpr.biblioteca.servico.estrategia.PoliticasMulta.Original());
    }

    // ---- Acesso aos tratadores (CLI) e a alguns componentes (só para os testes conferirem resultados) ----
    public FabricaPersistencia fabrica(){return fabrica;}
    public TratadorDeAcervo livros(){return livros;}
    public TratadorDePessoa pessoas(){return pessoas;}
    public TratadorDeEstudante estudantes(){return estudantes;}
    public TratadorDeAtendente atendentes(){return atendentes;}
    public TratadorDeEmprestimo emprestimos(){return emprestimos;}
    public TratadorDeDevolucao devolucoes(){return devolucoes;}
    public TratadorFinanceiro financeiro(){return financeiro;}
    public TratadorDeConsulta consultas(){return consultas;}
    public TratadorDeHierarquia hierarquia(){return hierarquia;}
    public Metrica metrica(){return metrica;}
    public Cache cache(){return cache;}
    public Publicador publicador(){return publicador;}
    public FilaInspecao inspecao(){return inspecao;}
    public Indicadores indicadores(){return indicadores;}
    public AvisoEmail avisoEmail(){return avisoEmail;}
    public ServicoMultas multas(){return multas;}
}
