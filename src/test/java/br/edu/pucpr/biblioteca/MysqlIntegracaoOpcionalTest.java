package br.edu.pucpr.biblioteca;

import br.edu.pucpr.biblioteca.dominio.Livro;
import br.edu.pucpr.biblioteca.dominio.Entidades.*;
import br.edu.pucpr.biblioteca.dominio.estados.EstadosLivro.Estado;
import br.edu.pucpr.biblioteca.persistencia.mysql.FabricaMysql;
import br.edu.pucpr.biblioteca.servico.SistemaBiblioteca;
import br.edu.pucpr.biblioteca.servico.banco.Bancos.*;
import br.edu.pucpr.biblioteca.servico.estrategia.PoliticasMulta.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Ative TEST_MYSQL_URL após inicializar o schema; nunca usa o banco de produção por padrão. */
class MysqlIntegracaoOpcionalTest {
    @Test @EnabledIfEnvironmentVariable(named="TEST_MYSQL_URL",matches=".+")
    void R08_integracaoRealMysqlTransacoesERecuperacaoDeEstado() {
        String sufixo=UUID.randomUUID().toString().substring(0,8);
        FabricaMysql f=new FabricaMysql(System.getenv("TEST_MYSQL_URL"),
                System.getenv().getOrDefault("TEST_MYSQL_USER","biblioteca"),
                System.getenv().getOrDefault("TEST_MYSQL_PASSWORD","biblioteca"));
        SistemaBiblioteca sistema=new SistemaBiblioteca(f,Clock.systemUTC(),
                new AdaptadorBancoB(new ApiBancoB()),new Original());
        String idLivro="TEST-L-"+sufixo,idEst="TEST-E-"+sufixo,cpf="TEST-CPF-"+sufixo;
        sistema.pessoas().inserirPessoa(new Pessoa(cpf,"Teste","MySQL","teste@example.org"));
        sistema.estudantes().inserirEstudante(new Estudante(idEst,cpf));
        sistema.livros().inserirLivro(new Livro(idLivro,"Persistido em MySQL"));
        sistema.emprestimos().solicitarReserva(idLivro,idEst);
        sistema.emprestimos().emprestarLivro(idLivro,idEst);
        assertNull(sistema.emprestimos().consultarReserva(idLivro,idEst));
        assertEquals(Estado.EMPRESTADO,sistema.livros().consultarLivro(idLivro).estado());
        SistemaBiblioteca novaInstancia=new SistemaBiblioteca(new FabricaMysql(
                System.getenv("TEST_MYSQL_URL"),
                System.getenv().getOrDefault("TEST_MYSQL_USER","biblioteca"),
                System.getenv().getOrDefault("TEST_MYSQL_PASSWORD","biblioteca")),
                Clock.systemUTC(),new AdaptadorBancoB(new ApiBancoB()),new Original());
        assertEquals(Estado.EMPRESTADO,novaInstancia.livros().consultarLivro(idLivro).estado());
        assertNotNull(novaInstancia.emprestimos().consultarEmprestimo(idLivro,idEst));
    }
}
