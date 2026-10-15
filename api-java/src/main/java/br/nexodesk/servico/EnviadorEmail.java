package br.nexodesk.servico;

/** Envia um e-mail de texto simples. Implementações: SMTP real ou simulada (só log). */
public interface EnviadorEmail {

    void enviar(String para, String assunto, String texto);
}
