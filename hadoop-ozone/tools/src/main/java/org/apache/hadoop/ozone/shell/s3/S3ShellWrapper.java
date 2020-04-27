package org.apache.hadoop.ozone.shell.s3;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;

import static org.apache.hadoop.ozone.shell.s3.StringUtils.fileToString;

public class S3ShellWrapper  {

    private static Logger LOG = LoggerFactory.getLogger(S3ShellWrapper.class);

    public static void main(String[] args) throws Exception
    {
        // set kerberos configurations.
        System.setProperty("java.security.krb5.conf", "/etc/krb5.conf");
        System.setProperty("sun.security.krb5.debug", "true");


        String principal = "mykidong/mc-d02.opasnet.io@OPASNET.IO";
        String keytab = "/etc/ozone/ozone.keytab";

        Configuration hadoopConfiguration = new Configuration();

        // ozone configuration.
        String ozoneConf = fileToString("ozone-site.xml");
        hadoopConfiguration.addResource(new ByteArrayInputStream(ozoneConf.getBytes()));

        UserGroupInformation.setConfiguration(hadoopConfiguration);

        UserGroupInformation ugi = UserGroupInformation.loginUserFromKeytabAndReturnUGI(principal, keytab);
        if(ugi == null) {
            ugi = UserGroupInformation.loginUserFromKeytabAndReturnUGI(principal, keytab);
            LOG.info("login done with kerberos!");
        } else {
            LOG.info("ugi: " + ugi.toString());

            ugi.checkTGTAndReloginFromKeytab();
            LOG.info("check tgt done with kerberos!");
        }

        new S3Shell().run(args);
    }
}
