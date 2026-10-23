package br.nexodesk.dominio;

public enum Perfil {
    MORADOR,
    /** Atende os chamados que o síndico atribui a ele (não vê os demais) e conversa com o morador. */
    FUNCIONARIO,
    SINDICO
}
