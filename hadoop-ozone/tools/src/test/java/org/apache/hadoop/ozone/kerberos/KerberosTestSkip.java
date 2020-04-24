package org.apache.hadoop.ozone.kerberos;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

import static org.apache.hadoop.ozone.kerberos.StringUtils.fileToString;

public class KerberosTestSkip {

    private static final Logger LOG =
            LoggerFactory.getLogger(KerberosTestSkip.class);

    @Before
    public void init() throws Exception {
        // init. log4j.
        Log4jConfigurer.loadLog4j("/log4j2-test.xml");
    }

    @Test
    public void runCLI() throws Exception
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

        String[] args = Arrays.asList("ozone", "-config", "/home/mc/ozone/ozone-0.4.1-alpha/etc/hadoop", "s3", "getsecret").toArray(new String[0]);

        new org.apache.hadoop.ozone.shell.s3.S3Shell().run(args);

        Thread.sleep(Long.MAX_VALUE);
    }
}
