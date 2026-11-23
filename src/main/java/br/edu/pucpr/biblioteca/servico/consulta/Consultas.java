package br.edu.pucpr.biblioteca.servico.consulta;

import br.edu.pucpr.biblioteca.dominio.Livro;
import br.edu.pucpr.biblioteca.dominio.Entidades.MetadadosLivro;
import br.edu.pucpr.biblioteca.persistencia.Contratos.UnidadeTrabalho;
import br.edu.pucpr.biblioteca.servico.Estruturas.Acervo;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * R09 — DECORATOR · papéis:
 *   COMPONENT           = {@link ConsultaAcervo}
 *   CONCRETE COMPONENT  = {@link Original} (implementação original, sem alterações)
 *   DECORATOR           = {@link Decorador} (guarda outro ConsultaAcervo e repassa a ele)
 *   CONCRETE DECORATORS = {@link Cache} e {@link Metrica}
 *
 * Montagem da cadeia (em SistemaBiblioteca):
 *     TratadorDeConsulta -> Metrica -> Cache -> Original
 * "Por que a ordem importa?" -> a Metrica fica POR FORA para contar TODA consulta, inclusive as que o
 * cache responde. Se fosse Cache(Metrica(Original)), um acerto de cache retornaria antes de passar
 * pela métrica e o teste R09 daria 1 acesso em vez de 2.
 *
 * "Por que o cache não participa da decisão de reserva?" -> ele guarda só MetadadosLivro (id e título),
 * que nem tem estado. A reserva vai por FachadaCirculacao.reservar -> acervo.exigir(...), que lê o
 * Livro atual do repositório, e o State decide. Um cache antigo nunca autoriza reserva.
 *
 * "Por que decorador e não herança?" -> cada combinação (só cache, só métrica, os dois) seria uma
 * subclasse; com decoradores combina-se na montagem, conforme a instalação, sem tocar em Original.
 */
public final class Consultas {
    private Consultas() { }

    /** COMPONENT: contrato de consulta ao acervo. Original e decoradores implementam o MESMO contrato. */
    public interface ConsultaAcervo { MetadadosLivro metadados(String id); }

    /** CONCRETE COMPONENT: consulta de verdade, lendo o Acervo dentro de uma transação. */
    public static final class Original implements ConsultaAcervo {
        private final Acervo acervo; private final UnidadeTrabalho unidade;
        public Original(Acervo a,UnidadeTrabalho u){acervo=a;unidade=u;}
        public MetadadosLivro metadados(String id){return unidade.executar(()->{
            Livro l=acervo.consultarLivro(id);
            return l==null?null:new MetadadosLivro(l.id(),l.titulo());   // só metadados, sem estado
        });}
    }

    /** DECORATOR abstrato: embrulha outro ConsultaAcervo (pode ser o Original ou outro decorador). */
    public static abstract class Decorador implements ConsultaAcervo {
        protected final ConsultaAcervo original;   // o próximo da cadeia
        protected Decorador(ConsultaAcervo original){this.original=original;}
    }

    /**
     * CONCRETE DECORATOR de CACHE: primeira consulta vai ao original e guarda; as seguintes saem do mapa.
     * ConcurrentHashMap: seguro com várias threads; putIfAbsent evita sobrescrever entrada já gravada.
     */
    public static final class Cache extends Decorador {
        private final ConcurrentMap<String,MetadadosLivro> itens=new ConcurrentHashMap<>();
        public Cache(ConsultaAcervo original){super(original);}
        /**
         * Falha do cache não derruba a consulta: degrada para o componente original.
         * Pergunta: "o que acontece se o cache falhar?" -> o erro é registrado e a consulta segue para o
         * original; fica mais lenta, mas funciona. A Metrica (por fora) continua contando.
         * Teste: R09_falhaDoCacheDegradaParaConsultaOriginal.
         */
        public MetadadosLivro metadados(String id){
            try {
                MetadadosLivro encontrado=itens.get(id);
                if(encontrado!=null)return encontrado;          // acerto de cache
            } catch(RuntimeException falha) {
                System.err.println("Cache indisponível: "+falha.getMessage());
                return original.metadados(id);                  // degrada para o original
            }
            MetadadosLivro retorno=original.metadados(id);      // falta no cache: consulta o original
            try { if(retorno!=null)itens.putIfAbsent(id,retorno); }
            catch(RuntimeException falha) { System.err.println("Cache indisponível: "+falha.getMessage()); }
            return retorno;
        }
        public int tamanho(){return itens.size();}
    }

    /** CONCRETE DECORATOR de MEDIÇÃO: conta o acesso e repassa. AtomicInteger = contagem segura entre threads. */
    public static final class Metrica extends Decorador {
        private final AtomicInteger acessos=new AtomicInteger();
        public Metrica(ConsultaAcervo original){super(original);}
        public MetadadosLivro metadados(String id){acessos.incrementAndGet();return original.metadados(id);}
        public int acessos(){return acessos.get();}
    }
}
