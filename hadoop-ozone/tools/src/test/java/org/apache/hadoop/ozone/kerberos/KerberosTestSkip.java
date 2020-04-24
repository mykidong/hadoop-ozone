package org.apache.hadoop.ozone.kerberos;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.junit.Test;

import java.util.Arrays;

public class KerberosTestSkip {

    @Test
    public void login() throws Exception
    {
        try {
            String princiapl = "mykidong/mc-d02.opasnet.io@OPASNET.IO";
            String keytab = "/etc/ozone/ozone.keytab";

            Configuration conf = new Configuration();
            conf.set("hadoop.security.authentication", "kerberos");
            UserGroupInformation.setConfiguration(conf);

            UserGroupInformation ugi = UserGroupInformation.loginUserFromKeytabAndReturnUGI(princiapl, keytab);
            if(ugi == null) {
                ugi = UserGroupInformation.loginUserFromKeytabAndReturnUGI(princiapl, keytab);
                System.out.println("login done with kerberos!");
            } else {
                System.out.println("ugi: " + ugi.toString());

                ugi.checkTGTAndReloginFromKeytab();
                System.out.println("check tgt done with kerberos!");
            }

            String[] args = Arrays.asList("getsecret").toArray(new String[0]);

            new org.apache.hadoop.ozone.shell.s3.S3Shell().run(args);

        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
