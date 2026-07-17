package com.youngstersclub.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "organization_settings")
public class OrganizationSettings {

  @Id
  @Column(name = "organization_id")
  private Long organizationId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organization_id")
  private Organization organization;

  @Column(length = 100)
  private String timezone;

  @Column(length = 10)
  private String currency;

  @Column(name = "whatsapp_phone_number", length = 30)
  private String whatsappPhoneNumber;

  @Column(name = "brevo_sender_email", length = 200)
  private String brevoSenderEmail;

  @Column(name = "business_name", length = 200)
  private String businessName;

  @Column(name = "gst_number", length = 50)
  private String gstNumber;

  @Column(name = "support_phone", length = 20)
  private String supportPhone;

  @Column(name = "support_email", length = 200)
  private String supportEmail;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  public OrganizationSettings() {}

  public OrganizationSettings(
      Long organizationId,
      Organization organization,
      String timezone,
      String currency,
      String whatsappPhoneNumber,
      String brevoSenderEmail,
      String businessName,
      String gstNumber,
      String supportPhone,
      String supportEmail,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {
    this.organizationId = organizationId;
    this.organization = organization;
    this.timezone = timezone;
    this.currency = currency;
    this.whatsappPhoneNumber = whatsappPhoneNumber;
    this.brevoSenderEmail = brevoSenderEmail;
    this.businessName = businessName;
    this.gstNumber = gstNumber;
    this.supportPhone = supportPhone;
    this.supportEmail = supportEmail;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public Long getOrganizationId() { return organizationId; }
  public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }
  public Organization getOrganization() { return organization; }
  public void setOrganization(Organization organization) { this.organization = organization; }
  public String getTimezone() { return timezone; }
  public void setTimezone(String timezone) { this.timezone = timezone; }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
  public String getWhatsappPhoneNumber() { return whatsappPhoneNumber; }
  public void setWhatsappPhoneNumber(String whatsappPhoneNumber) { this.whatsappPhoneNumber = whatsappPhoneNumber; }
  public String getBrevoSenderEmail() { return brevoSenderEmail; }
  public void setBrevoSenderEmail(String brevoSenderEmail) { this.brevoSenderEmail = brevoSenderEmail; }
  public String getBusinessName() { return businessName; }
  public void setBusinessName(String businessName) { this.businessName = businessName; }
  public String getGstNumber() { return gstNumber; }
  public void setGstNumber(String gstNumber) { this.gstNumber = gstNumber; }
  public String getSupportPhone() { return supportPhone; }
  public void setSupportPhone(String supportPhone) { this.supportPhone = supportPhone; }
  public String getSupportEmail() { return supportEmail; }
  public void setSupportEmail(String supportEmail) { this.supportEmail = supportEmail; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
