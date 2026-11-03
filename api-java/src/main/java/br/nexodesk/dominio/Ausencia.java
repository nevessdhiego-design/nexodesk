package br.nexodesk.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Período em que um funcionário não está disponível (férias, folga, atestado). O síndico registra; o painel deixa de indicá-lo. */
@Entity
@Table(name = "ausencias")
public class Ausencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoAusencia tipo;

    @Column(name = "de", nullable = false)
    private LocalDate de;

    @Column(name = "ate", nullable = false)
    private LocalDate ate;

    @Column(length = 120)
    private String observacao;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    void aoCriar() {
        criadoEm = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public TipoAusencia getTipo() { return tipo; }
    public void setTipo(TipoAusencia tipo) { this.tipo = tipo; }
    public LocalDate getDe() { return de; }
    public void setDe(LocalDate de) { this.de = de; }
    public LocalDate getAte() { return ate; }
    public void setAte(LocalDate ate) { this.ate = ate; }
    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
}
