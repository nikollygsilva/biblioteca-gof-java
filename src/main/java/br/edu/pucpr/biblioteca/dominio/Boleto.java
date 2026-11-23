package br.edu.pucpr.biblioteca.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Boleto de multa (resultado do R02 Strategy + R03 Adapter).
 *
 * Ponto importante da arguição do R02: "por que trocar a política não recalcula multas já emitidas?"
 *   -> o boleto CONGELA, no momento da emissão, o valor calculado e o NOME da política aplicada
 *      (campos final). Depois disso ele é um fato registrado, não um cálculo.
 * Os únicos campos que mudam depois são quitado e idCobranca.
 */
public final class Boleto {
    private final String id, idDevolucao, idEstudante, politica;   // politica = nome da Strategy usada (ex.: ORIGINAL)
    private final BigDecimal valor;                                // BigDecimal: dinheiro nunca em double
    private final long diasAtraso;
    private final LocalDate emissao;
    private boolean quitado;                                       // muda só uma vez (quitação idempotente, R03)
    private String idCobranca;                                     // identificador devolvido pelo banco (ex.: "A-...")

    public Boleto(String id, String idDevolucao, String idEstudante, String politica,
                  BigDecimal valor, long diasAtraso, LocalDate emissao, boolean quitado, String idCobranca) {
        this.id=id; this.idDevolucao=idDevolucao; this.idEstudante=idEstudante;
        this.politica=politica; this.valor=valor; this.diasAtraso=diasAtraso;
        this.emissao=emissao; this.quitado=quitado; this.idCobranca=idCobranca;
    }
    /** Cópia defensiva para o repositório em memória. */
    public Boleto copia() { return new Boleto(id,idDevolucao,idEstudante,politica,valor,diasAtraso,emissao,quitado,idCobranca); }
    public String id() { return id; }
    public String idDevolucao() { return idDevolucao; }
    public String idEstudante() { return idEstudante; }
    public String politica() { return politica; }
    public BigDecimal valor() { return valor; }
    public long diasAtraso() { return diasAtraso; }
    public LocalDate emissao() { return emissao; }
    public boolean quitado() { return quitado; }
    public String idCobranca() { return idCobranca; }
    /** Guarda o identificador externo que o adaptador do banco devolveu. */
    public void registrarCobranca(String idExterno) { idCobranca=idExterno; }
    /** Marca como pago. Quem impede a segunda quitação é ServicoMultas.confirmar (checa quitado() antes). */
    public void quitar() { quitado=true; }
}
