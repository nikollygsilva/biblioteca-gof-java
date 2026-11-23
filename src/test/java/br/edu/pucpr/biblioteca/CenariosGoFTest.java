package br.edu.pucpr.biblioteca;

import br.edu.pucpr.biblioteca.dominio.*;
import br.edu.pucpr.biblioteca.dominio.Entidades.*;
import br.edu.pucpr.biblioteca.dominio.estados.EstadosLivro.Estado;
import br.edu.pucpr.biblioteca.persistencia.Contratos.*;
import br.edu.pucpr.biblioteca.persistencia.memoria.FabricaMemoria;
import br.edu.pucpr.biblioteca.persistencia.mysql.FabricaMysql;
import br.edu.pucpr.biblioteca.servico.*;
import br.edu.pucpr.biblioteca.servico.Estruturas.*;
import br.edu.pucpr.biblioteca.servico.arvore.HierarquiaAcervo.*;
import br.edu.pucpr.biblioteca.servico.banco.Bancos.*;
import br.edu.pucpr.biblioteca.servico.comandos.Comandos.*;
import br.edu.pucpr.biblioteca.servico.consulta.Consultas.*;
import br.edu.pucpr.biblioteca.servico.estrategia.PoliticasMulta.*;
import br.edu.pucpr.biblioteca.servico.eventos.Devolucoes.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Os dez cenários obrigatórios + testes adicionais: rollback (R04), exclusão pelo State (R01) e falha do cache (R09). */
class CenariosGoFTest {
    private static final Clock RELOGIO=Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    private static SistemaBiblioteca novo(){return SistemaBiblioteca.paraTestes(RELOGIO);}
    private static void cadastrar(SistemaBiblioteca s,String livro,String estudante){
        s.pessoas().inserirPessoa(new Pessoa("CPF-"+estudante,"Maria","Teste","maria@example.org"));
        s.estudantes().inserirEstudante(new Estudante(estudante,"CPF-"+estudante));
        s.livros().inserirLivro(new Livro(livro,"Engenharia de Software"));
    }
    private static void emprestar(SistemaBiblioteca s,String livro,String estudante){
        cadastrar(s,livro,estudante);
        s.emprestimos().solicitarReserva(livro,estudante);
        s.emprestimos().emprestarLivro(livro,estudante);
    }
    private static Devolucao devolverAtrasado(SistemaBiblioteca s,String livro,String estudante,int dias){
        Emprestimo e=s.emprestimos().consultarEmprestimo(livro,estudante);
        s.devolucoes().registrarDevolucaoEm(livro,estudante,e.vencimento().plusDays(dias));
        return s.devolucoes().consultarUltimaDevolucao(livro,estudante);
    }

    /** R01 State: 3ª renovação recusada sem mudar o vencimento; DEVOLVIDO não aceita reserva; inspeção ruim -> REPARANDO; laudo -> INCINERADO. */
    @Test void R01_terceiraRenovacaoRecusadaEstadoPreservadoEInspecaoObrigatoria(){
        SistemaBiblioteca s=novo();emprestar(s,"L1","E1");
        s.emprestimos().renovarEmprestimo("L1","E1");
        s.emprestimos().renovarEmprestimo("L1","E1");
        LocalDate venc=s.emprestimos().consultarEmprestimo("L1","E1").vencimento();
        assertThrows(IllegalStateException.class,()->s.emprestimos().renovarEmprestimo("L1","E1"));
        assertEquals(venc,s.emprestimos().consultarEmprestimo("L1","E1").vencimento());
        assertEquals(Estado.RENOVADO,s.livros().consultarLivro("L1").estado());
        devolverAtrasado(s,"L1","E1",0);
        assertEquals(Estado.DEVOLVIDO,s.livros().consultarLivro("L1").estado());
        assertThrows(IllegalStateException.class,()->s.emprestimos().solicitarReserva("L1","E1"));
        s.devolucoes().inspecionarLivro("L1",false);
        assertEquals(Estado.REPARANDO,s.livros().consultarLivro("L1").estado());
        s.devolucoes().laudoIrrecuperavel("L1");
        assertEquals(Estado.INCINERADO,s.livros().consultarLivro("L1").estado());
    }

    /** R01 State: excluir só é aceito em LIVRE (o tratador não tem if; o estado recusa). */
    @Test void R01_excluirLivroForaDeLivreRecusadoPeloEstado(){
        SistemaBiblioteca s=novo();emprestar(s,"LE","EE");
        assertThrows(IllegalStateException.class,()->s.livros().excluirLivro("LE"));
        assertNotNull(s.livros().consultarLivro("LE"));
        assertEquals(Estado.EMPRESTADO,s.livros().consultarLivro("LE").estado());
        s.livros().inserirLivro(new Livro("LL","Livro livre"));
        s.livros().excluirLivro("LL");
        assertNull(s.livros().consultarLivro("LL"));
    }

    /** R02 Strategy: 5 dias -> R$ 5,00 (original) e R$ 3,00 (tolerância); teto R$ 20,00; sem atraso zero; trocar a política não recalcula boleto emitido. */
    @Test void R02_cincoDiasOriginalCincoToleranciaTresSemAtrasoZero(){
        assertEquals(new BigDecimal("5.00"),new br.edu.pucpr.biblioteca.servico.estrategia.PoliticasMulta.Original().calcular(5));
        assertEquals(new BigDecimal("3.00"),new ComTolerancia().calcular(5));
        assertEquals(new BigDecimal("20.00"),new ComTeto().calcular(90));
        assertEquals(new BigDecimal("0.00"),new br.edu.pucpr.biblioteca.servico.estrategia.PoliticasMulta.Original().calcular(0));
        assertEquals(new BigDecimal("0.00"),new ComTolerancia().calcular(0));
        SistemaBiblioteca s=novo();emprestar(s,"L2","E2");
        Devolucao d=devolverAtrasado(s,"L2","E2",5);
        Boleto b=s.devolucoes().emitirBoletoMulta(d.id());
        s.multas().definirPolitica(new ComTolerancia());
        assertEquals("ORIGINAL",b.politica());
        assertEquals(new BigDecimal("5.00"),s.financeiro().consultarBoleto(b.id()).valor());
    }

    /** R03 Adapter: R$ 7,00 vira 700 centavos no A e 7.00 no B; CPF convertido nos dois sentidos (A tira a máscara, B põe; B recusa CPF sem 11 dígitos); a 2ª notificação de pagamento não quita de novo. */
    @Test void R03_mesmaCobrancaSeteReaisEmCentavosOuDecimalENotificacaoIdempotente(){
        Boleto boleto=new Boleto("B1","D1","E1","ORIGINAL",new BigDecimal("7.00"),7,
                LocalDate.of(2026,11,1),false,null);
        ApiBancoA apiA=new ApiBancoA();ApiBancoB apiB=new ApiBancoB();
        CobrancaInstitucional a=new AdaptadorBancoA(apiA),b=new AdaptadorBancoB(apiB);
        assertEquals("A-B1",a.cobrar(boleto,"123.456.789-00").identificadorExterno());
        assertEquals("B-B1",b.cobrar(boleto,"12345678900").identificadorExterno());   // B recebe SEM máscara...
        assertEquals(700,apiA.ultimosCentavos);
        assertEquals(new BigDecimal("7.00"),apiB.ultimosReais);
        assertEquals("12345678900",apiA.ultimoDocumento);
        assertEquals("123.456.789-00",apiB.ultimoDocumento);                           // ...e o adaptador formata
        b.cobrar(boleto,"123.456.789-00");                                             // com máscara continua igual
        assertEquals("123.456.789-00",apiB.ultimoDocumento);
        assertThrows(IllegalArgumentException.class,()->b.cobrar(boleto,"123"));      // sem 11 dígitos: recusado
        SistemaBiblioteca s=novo();emprestar(s,"L3","E3");
        Boleto emitido=s.devolucoes().emitirBoletoMulta(devolverAtrasado(s,"L3","E3",7).id());
        assertTrue(s.financeiro().confirmarPagamento(emitido.id(),"00"));
        assertFalse(s.financeiro().confirmarPagamento(emitido.id(),"00"));
        assertTrue(s.financeiro().consultarBoleto(emitido.id()).quitado());
    }

    /** R04 Facade: efetivar empréstimo remove a reserva, cria o empréstimo e deixa o livro EMPRESTADO. */
    @Test void R04_fachadaConsomeReservaCriaEmprestimoEAtualizaLivro(){
        SistemaBiblioteca s=novo();cadastrar(s,"L4","E4");
        s.emprestimos().solicitarReserva("L4","E4");
        s.emprestimos().emprestarLivro("L4","E4");
        assertNull(s.emprestimos().consultarReserva("L4","E4"));
        assertNotNull(s.emprestimos().consultarEmprestimo("L4","E4"));
        assertEquals(Estado.EMPRESTADO,s.livros().consultarLivro("L4").estado());
    }

    /** R04 Facade: um Proxy faz salvarEmprestimo falhar depois de remover a reserva; o rollback devolve tudo ao estado anterior. */
    @Test void R04_falhaNoMeioDaFachadaReverteReservaEEmprestimo() {
        FabricaMemoria familia=new FabricaMemoria();
        SistemaBiblioteca s=new SistemaBiblioteca(familia,RELOGIO,new AdaptadorBancoA(new ApiBancoA()),new br.edu.pucpr.biblioteca.servico.estrategia.PoliticasMulta.Original());
        cadastrar(s,"LF","EF");s.emprestimos().solicitarReserva("LF","EF");
        // Injeção de falha apenas na criação de empréstimo, após a exclusão da reserva.
        RepositorioMovimentacao comFalha=(RepositorioMovimentacao)Proxy.newProxyInstance(
                getClass().getClassLoader(),new Class<?>[]{RepositorioMovimentacao.class},(proxy,metodo,params)->{
                    if(metodo.getName().equals("salvarEmprestimo"))throw new IllegalStateException("Falha simulada");
                    try{return metodo.invoke(familia.movimentacao(),params);}
                    catch(InvocationTargetException e){throw e.getCause();}
                });
        FachadaCirculacao fachada=new FachadaCirculacao(new Acervo(familia.livros()),
                new Relacionamento(familia.relacionamento()),new Movimentacao(comFalha),
                familia.unidadeTrabalho(),new Publicador(),RELOGIO);
        assertThrows(IllegalStateException.class,()->fachada.emprestar("LF","EF"));
        assertNotNull(s.emprestimos().consultarReserva("LF","EF"));
        assertNull(s.emprestimos().consultarEmprestimo("LF","EF"));
        assertEquals(Estado.RESERVADO,s.livros().consultarLivro("LF").estado());
    }

    /** R05 Observer: um ouvinte de e-mail que falha não impede inspeção, multa e indicadores; no prazo não há multa; livro fica DEVOLVIDO. */
    @Test void R05_devolucaoAtrasadaDisparaOuvintesSemQueFalhaDeEmailImpeça(){
        SistemaBiblioteca s=novo();emprestar(s,"L5","E5");
        s.publicador().adicionar(e->{throw new IllegalStateException("Servidor de e-mail fora");});
        Devolucao d=devolverAtrasado(s,"L5","E5",5);
        assertTrue(s.inspecao().idsAguardando().contains("L5"));
        assertEquals(1,s.indicadores().total());
        assertEquals(1,s.indicadores().atrasadas());
        assertNotNull(s.devolucoes().emitirBoletoMulta(d.id()));
        assertEquals(Estado.DEVOLVIDO,s.livros().consultarLivro("L5").estado());
        assertEquals(1,s.publicador().falhas().size());
        emprestar(s,"L6","E6");
        Devolucao noPrazo=devolverAtrasado(s,"L6","E6",0);
        assertNull(s.devolucoes().emitirBoletoMulta(noPrazo.id()));
        assertEquals(2,s.indicadores().total());
    }

    /** R06 Command: 2 threads submetem o MESMO comando -> uma executa e a outra é IGNORADA (+30 dias só uma vez); comandos novos seguem o limite de 2. */
    @Test void R06_renovacaoSimultaneaMesmoUuidAcrescentaSoTrintaDias() throws Exception {
        SistemaBiblioteca s=novo();emprestar(s,"L7","E7");
        LocalDate original=s.emprestimos().consultarEmprestimo("L7","E7").vencimento();
        Renovacao comando=new Renovacao(UUID.randomUUID(),"E7",RELOGIO.instant(),"L7","E7");
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<Comprovante> a=pool.submit(()->s.emprestimos().submeter(comando));
            Future<Comprovante> b=pool.submit(()->s.emprestimos().submeter(comando));
            Set<String> resultados=Set.of(a.get(5,TimeUnit.SECONDS).operacao(),b.get(5,TimeUnit.SECONDS).operacao());
            assertEquals(Set.of("RENOVAÇÃO","IGNORADO"),resultados);
        } finally {pool.shutdownNow();}
        assertEquals(original.plusDays(30),s.emprestimos().consultarEmprestimo("L7","E7").vencimento());
        s.emprestimos().submeter(new Renovacao(UUID.randomUUID(),"E7",RELOGIO.instant(),"L7","E7"));
        assertThrows(IllegalStateException.class,()->s.emprestimos().submeter(new Renovacao(UUID.randomUUID(),"E7",RELOGIO.instant(),"L7","E7")));
        assertEquals(2,s.emprestimos().consultarEmprestimo("L7","E7").numRenovacoes());
    }

    /** R07 Template Method: duplicado devolve o existente; CPF sem pessoa falha antes de gravar. */
    @Test void R07_estudanteDuplicadoRetornaExistenteENovoSemPessoaNaoPersiste(){
        SistemaBiblioteca s=novo();
        s.pessoas().inserirPessoa(new Pessoa("CPF-7","Bia","Exemplo","bia@example.org"));
        s.estudantes().inserirEstudante(new Estudante("E7","CPF-7"));
        s.estudantes().inserirEstudante(new Estudante("E7","CPF-outro"));
        assertEquals("CPF-7",s.estudantes().consultarEstudante("E7").cpf());
        assertThrows(IllegalStateException.class,()->s.estudantes().inserirEstudante(new Estudante("E8","CPF-inexistente")));
        assertNull(s.estudantes().consultarEstudante("E8"));
    }

    /** R08 Abstract Factory: perfil de teste só entrega objetos em memória; perfil mysql só entrega repositórios MySQL e a UnidadeMysql. */
    @Test void R08_familiasEmMemoriaEMysqlNaoMisturamRepositorios(){
        FabricaPersistencia teste=SistemaBiblioteca.criarFamilia("teste","inutil","x","x");
        FabricaPersistencia prod=SistemaBiblioteca.criarFamilia("mysql","jdbc:mysql://localhost:3306/biblioteca_gof","root","");
        assertInstanceOf(FabricaMemoria.class,teste);
        assertInstanceOf(FabricaMysql.class,prod);
        assertInstanceOf(FabricaMysql.UnidadeMysql.class,prod.unidadeTrabalho());
        assertTrue(teste.livros().getClass().getName().contains("memoria"));
        assertTrue(teste.relacionamento().getClass().getName().contains("memoria"));
        assertTrue(teste.movimentacao().getClass().getName().contains("memoria"));
        assertTrue(prod.livros().getClass().getName().contains("mysql"));
        assertTrue(prod.relacionamento().getClass().getName().contains("mysql"));
        assertTrue(prod.movimentacao().getClass().getName().contains("mysql"));
        // O construtor relacional é lazy e NÃO precisa de MySQL ativo para esta verificação.
    }

    /** R09 Decorator: 2ª consulta sai do cache mas a métrica conta 2; cache antigo não autoriza reservar livro que deixou de estar LIVRE. */
    @Test void R09_segundaConsultaUsaCacheMasMetricaContaEEstadoAtualBloqueiaReserva(){
        SistemaBiblioteca s=novo();cadastrar(s,"L9","E9");
        assertEquals("L9",s.consultas().consultarMetadados("L9").id());
        assertEquals("L9",s.consultas().consultarMetadados("L9").id());
        assertEquals(2,s.metrica().acessos());
        assertEquals(1,s.cache().tamanho());
        s.emprestimos().solicitarReserva("L9","E9");
        assertNotNull(s.consultas().consultarMetadados("L9")); // cache antigo continua válido apenas para metadados
        assertThrows(IllegalStateException.class,()->s.emprestimos().solicitarReserva("L9","E9"));
    }

    /** R09 Decorator: troca o mapa do cache por um que sempre falha; a consulta continua respondendo pelo original. */
    @Test void R09_falhaDoCacheDegradaParaConsultaOriginal() throws Exception {
        SistemaBiblioteca s=novo();cadastrar(s,"LC","EC");
        Field itens=Cache.class.getDeclaredField("itens");
        itens.setAccessible(true);
        itens.set(s.cache(),new ConcurrentHashMap<String,MetadadosLivro>(){
            @Override public MetadadosLivro get(Object chave){throw new IllegalStateException("Cache fora do ar");}
        });
        assertEquals("LC",s.consultas().consultarMetadados("LC").id());
        assertEquals(1,s.metrica().acessos());
    }

    /** R10 Composite: 3 + 2 = 5 livros; recusa livro em dois grupos e ciclo; mover não muda estado nem empréstimo do livro. */
    @Test void R10_compositeContaCincoETransfereSemAlterarLivro(){
        Registro registro=new Registro();
        Grupo unidade=new Grupo("Central",registro),a=new Grupo("Exatas",registro),b=new Grupo("Humanas",registro);
        unidade.adicionarGrupo(a);unidade.adicionarGrupo(b);
        a.adicionarLivro("L1");a.adicionarLivro("L2");a.adicionarLivro("L3");
        b.adicionarLivro("L4");b.adicionarLivro("L5");
        assertEquals(5,unidade.contar());
        assertThrows(IllegalStateException.class,()->b.adicionarLivro("L1"));
        assertThrows(IllegalArgumentException.class,()->a.adicionarGrupo(unidade));
        SistemaBiblioteca s=novo();emprestar(s,"L1","E1");
        Estado antes=s.livros().consultarLivro("L1").estado();
        a.moverLivroPara("L1",b);
        assertEquals(5,unidade.contar());
        assertEquals(2,a.contar()); assertEquals(3,b.contar());
        assertTrue(b.livros().contains("L1"));
        assertEquals(antes,s.livros().consultarLivro("L1").estado());
        assertNotNull(s.emprestimos().consultarEmprestimo("L1","E1"));
    }
}
