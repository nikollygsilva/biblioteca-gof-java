package br.edu.pucpr.biblioteca.persistencia.mysql;

import br.edu.pucpr.biblioteca.persistencia.Contratos.*;
import java.sql.*;
import java.util.function.Supplier;

/**
 * R08 — ABSTRACT FACTORY · papel: CONCRETE FACTORY da família RELACIONAL (perfil mysql/producao).
 *
 * Abstract Factory relacional. Os três repositórios usam exatamente a mesma conexão de transação.
 *
 * "Como os repositórios compartilham a mesma transação?" -> a fábrica cria UMA UnidadeMysql e passa
 * essa mesma instância ao construtor dos três repositórios; todos pegam a conexão por unidade.conexao().
 * "O que impede misturar com a família em memória?" -> os repositórios MySQL são package-private e
 * exigem uma UnidadeMysql; só esta fábrica consegue criá-los e ligá-los.
 * O Oracle (réplica do Trabalho 02) não aparece: nenhuma fábrica escreve nele.
 * O construtor NÃO abre conexão (só ao executar a primeira transação) — por isso o teste R08 roda sem MySQL.
 */
public final class FabricaMysql implements FabricaPersistencia {
    private final UnidadeMysql unidade;
    private final RepositorioLivro livros;
    private final RepositorioRelacionamento relacionamento;
    private final RepositorioMovimentacao movimentacao;

    public FabricaMysql(String jdbcUrl, String usuario, String senha) {
        this.unidade=new UnidadeMysql(jdbcUrl, usuario, senha);
        this.livros=new RepositoriosMysql.Livros(unidade);
        this.relacionamento=new RepositoriosMysql.RelacionamentoMysql(unidade);
        this.movimentacao=new RepositoriosMysql.MovimentacaoMysql(unidade);
    }
    @Override public UnidadeTrabalho unidadeTrabalho() { return unidade; }
    @Override public RepositorioLivro livros() { return livros; }
    @Override public RepositorioRelacionamento relacionamento() { return relacionamento; }
    @Override public RepositorioMovimentacao movimentacao() { return movimentacao; }

    /**
     * Transação JDBC com BEGIN/COMMIT/ROLLBACK, sem conexão compartilhada entre threads.
     * A conexão aberta fica num ThreadLocal durante a transação; autoCommit=false; no fim, commit;
     * se escapar exceção, rollback e a exceção é relançada.
     */
    public static final class UnidadeMysql implements UnidadeTrabalho {
        private final String url, usuario, senha;
        private final ThreadLocal<Connection> corrente=new ThreadLocal<>();
        UnidadeMysql(String url,String usuario,String senha) { this.url=url;this.usuario=usuario;this.senha=senha; }
        /** Usada pelos repositórios: devolve a conexão da transação corrente; fora de transação, erro. */
        Connection conexao() {
            Connection c=corrente.get();
            if (c == null) throw new IllegalStateException("Acesso SQL fora da unidade de trabalho");
            return c;
        }
        @Override public <T> T executar(Supplier<T> bloco) {
            if (corrente.get() != null) return bloco.get(); // mesmo contexto para operações aninhadas
            try (Connection c=DriverManager.getConnection(url,usuario,senha)) {
                c.setAutoCommit(false); corrente.set(c);
                try {
                    T retorno=bloco.get();
                    c.commit(); return retorno;
                } catch (RuntimeException | Error ex) {
                    try { c.rollback(); } catch(SQLException erro) { ex.addSuppressed(erro); }
                    throw ex;
                } finally { corrente.remove(); }
            } catch(SQLException ex) { throw new IllegalStateException("Falha de conexão/transação MySQL",ex); }
        }
    }
}
