package br.edu.pucpr.biblioteca.servico.arvore;

import java.util.*;

/**
 * R10 — COMPOSITE · papéis:
 *   COMPONENT = {@link No}    (operações comuns: contar e listar)
 *   LEAF      = {@link Item}  (referencia um livro só pelo ID)
 *   COMPOSITE = {@link Grupo} (unidade, seção ou subseção — a mesma classe, em qualquer profundidade)
 *   {@link Registro} = índice livroId -> Item compartilhado pela árvore toda (garante que um livro está em um só grupo)
 * Acesso pela CLI: TratadorDeHierarquia (opções 18 a 21 do menu).
 *
 * "Por que mover um item não toca no Livro nem na Movimentacao?" -> o Item guarda apenas o id do livro;
 * a árvore é só organização. Estado e empréstimo ficam em Livro/Movimentacao, que nem são chamados.
 * Observação: a árvore vive em memória enquanto o processo roda (também no perfil MySQL).
 */
public final class HierarquiaAcervo {
    private HierarquiaAcervo() { }

    /** COMPONENT: grupos e itens respondem às MESMAS operações, então o cliente não precisa de if (é grupo?). */
    public interface No {
        int contar();
        List<String> livros();
    }

    /** Escopo do acervo: duas seções não podem conter o mesmo id. */
    public static final class Registro {
        private final Map<String,Item> associados=new HashMap<>();   // livroId -> Item onde ele está
    }

    /** LEAF: um livro na árvore, identificado só pelo id (não guarda o objeto Livro). */
    public static final class Item implements No {
        private final String livroId;
        private Grupo pai;
        public Item(String id) {this.livroId=Objects.requireNonNull(id);}
        public String id(){return livroId;}
        /** Uma folha conta como 1 livro: é o caso base da recursão. */
        @Override public int contar(){return 1;}
        @Override public List<String> livros(){return List.of(livroId);}
    }

    /** COMPOSITE: guarda filhos (grupos ou itens) e repassa as operações a eles. */
    public static final class Grupo implements No {
        private final String nome;
        private final Registro registro;
        private final List<No> filhos=new ArrayList<>();
        private Grupo pai;
        public Grupo(String nome,Registro registro){this.nome=nome;this.registro=registro;}
        public String nome(){return nome;}

        /**
         * Grupos podem ter qualquer profundidade; um ancestral nunca pode ser filho.
         * Regras contra ciclo (pergunta da arguição):
         *   1. o novo grupo tem que ser do MESMO acervo (mesmo Registro);
         *   2. o novo grupo não pode já ter pai (um grupo não fica em dois lugares);
         *   3. sobe de this até a raiz pelos pais: se encontrar o próprio novo, recusa (seria ciclo).
         */
        public synchronized void adicionarGrupo(Grupo novo) {
            if(novo.registro!=registro)throw new IllegalArgumentException("Acervos distintos");
            if(novo.pai!=null)throw new IllegalStateException("Grupo já vinculado");
            for(Grupo atual=this;atual!=null;atual=atual.pai)
                if(atual==novo)throw new IllegalArgumentException("Ciclo recusado");
            filhos.add(novo);novo.pai=this;
        }
        /** Associa um livro. Recusa se o id já pertence a QUALQUER grupo da árvore (consulta o Registro). */
        public synchronized Item adicionarLivro(String id) {
            if(registro.associados.containsKey(id))throw new IllegalStateException("Livro já pertence a um grupo: "+id);
            Item item=new Item(id);item.pai=this; filhos.add(item);registro.associados.put(id,item);return item;
        }
        /**
         * Move um livro deste grupo para outro: só troca o Item de lista e atualiza o pai.
         * O id, o estado e o empréstimo do livro não mudam (cenário do R10).
         */
        public synchronized void moverLivroPara(String id,Grupo destino) {
            if(destino.registro!=registro)throw new IllegalArgumentException("Outro acervo");
            Item item=registro.associados.get(id);
            if(item==null || item.pai!=this)throw new IllegalArgumentException("Livro não está nesta seção");
            if(this==destino)return;
            filhos.remove(item);destino.filhos.add(item);item.pai=destino;
        }
        /**
         * "Como a contagem percorre a árvore?" -> recursão: soma contar() de cada filho.
         * Item devolve 1, Grupo soma os filhos. Ex.: unidade com seções de 3 e 2 livros -> 5.
         */
        @Override public int contar(){return filhos.stream().mapToInt(No::contar).sum();}
        /** Mesma ideia da contagem, juntando os ids de todos os itens abaixo deste grupo. */
        @Override public List<String> livros(){return filhos.stream().flatMap(n->n.livros().stream()).toList();}
    }
}
