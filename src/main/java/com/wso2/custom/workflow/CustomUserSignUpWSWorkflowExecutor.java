package com.wso2.custom.workflow;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.WorkflowResponse;
import org.wso2.carbon.apimgt.impl.dto.WorkflowDTO;
import org.wso2.carbon.apimgt.impl.workflow.GeneralWorkflowResponse;
import org.wso2.carbon.apimgt.impl.workflow.UserSignUpWSWorkflowExecutor;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowException;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowStatus;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.user.api.UserRealm;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.api.UserStoreManager;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;

import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.Message;
import javax.mail.MessagingException;

import java.util.Properties;

public class CustomUserSignUpWSWorkflowExecutor extends UserSignUpWSWorkflowExecutor {

    private static final Log log = LogFactory.getLog(CustomUserSignUpWSWorkflowExecutor.class);
    public static final String EMAIL_CLAIM = "http://wso2.org/claims/emailaddress";
    public static final String MAIL_SMTP_AUTH = "mail.smtp.auth";
    public static final String MAIL_SMTP_STAR_TTLS_ENABLE = "mail.smtp.starttls.enable";
    public static final String MAIL_SMTP_HOST = "mail.smtp.host";
    public static final String MAIL_SMTP_PORT = "mail.smtp.port";
    private String emailAddress;
    private String emailPassword;

    @Override
    public WorkflowResponse complete(WorkflowDTO workflowDTO) throws WorkflowException {

        super.complete(workflowDTO);
        Properties props = new Properties();
        props.put(MAIL_SMTP_AUTH, "true");
        props.put(MAIL_SMTP_STAR_TTLS_ENABLE, "true");
        props.put(MAIL_SMTP_HOST, "smtp.gmail.com");
        props.put(MAIL_SMTP_PORT, "587");
        Session session = Session.getInstance(props, new javax.mail.Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(emailAddress, emailPassword);
            }
        });
        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(emailAddress));
            String email = getUserEmail(workflowDTO);
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email));
            message.setSubject("My Company User Sign-up Service");
            if (WorkflowStatus.APPROVED.equals(workflowDTO.getStatus())) {
                message.setText("Your request to sign-up with <Company name> has been approved by the admin. Your " +
                        "username is "+ MultitenantUtils.getTenantAwareUsername(workflowDTO.getWorkflowReference()) +
                        ". You can now login with your credentials provided to the API store.");
            } else {
                message.setText("Your request to sign-up with <Company name> has been declined by the admin. " +
                        "Please contact the admin for more details.");
            }
            Transport.send(message);
        } catch (AddressException e) {
            String msg = "Error while converting the email address.";
            log.error(msg);
            throw new WorkflowException(e.getMessage());
        } catch (MessagingException e) {
            String msg = "Error while sending the sign-up update mail to user: " + workflowDTO.getWorkflowReference();
            log.error(msg);
            throw new WorkflowException(e.getMessage());
        }
        return new GeneralWorkflowResponse();
    }

    private String getUserEmail(WorkflowDTO workflowDTO) throws WorkflowException {
        PrivilegedCarbonContext ctx = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        ctx.setUsername(workflowDTO.getWorkflowReference());
        RealmService realmService = (RealmService) ctx.getOSGiService(RealmService.class, null);
        if (realmService == null) {
            String msg = "RealmService is not initialized";
            log.error(msg);
            throw new WorkflowException(msg);
        }
        String tenantAwareUserName = MultitenantUtils.getTenantAwareUsername(workflowDTO.getWorkflowReference());
        try {
            UserRealm userRealm = realmService.getTenantUserRealm(workflowDTO.getTenantId());
            UserStoreManager userStoreManager = userRealm.getUserStoreManager();
            return userStoreManager.getUserClaimValue(tenantAwareUserName, EMAIL_CLAIM, null);
        } catch (UserStoreException e) {
            String msg = "Error while getting email address of user: " + workflowDTO.getWorkflowReference();
            log.error(msg);
            throw new WorkflowException(e.getMessage());
        }
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public String getEmailPassword() {
        return emailPassword;
    }

    public void setEmailPassword(String emailPassword) {
        this.emailPassword = emailPassword;
    }
}
