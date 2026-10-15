package br.nexodesk.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Nota de 1 a 5 que o morador dá ao atendimento depois que o chamado é resolvido. */
@Entity
@Table(name = "avaliacoes")
public class Avaliacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chamado_id", nullable = false, unique = true)
    private Chamado chamado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "morador_id", nullable = false)
    private Usuario morador;

    @Column(nullable = false)
    private int nota;

    @Column(length = 500)
    private String comentario;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    void aoCriar() {
        criadoEm = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Chamado getChamado() { return chamado; }
    public void setChamado(Chamado chamado) { this.chamado = chamado; }
    public Usuario getMorador() { return morador; }
    public void setMorador(Usuario morador) { this.morador = morador; }
    public int getNota() { return nota; }
    public void setNota(int nota) { this.nota = nota; }
    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
}
