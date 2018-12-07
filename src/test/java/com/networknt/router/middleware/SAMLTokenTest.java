package com.networknt.router.middleware;

import com.networknt.router.TestServer;
import com.networknt.client.Http2Client;
import com.networknt.common.DecryptUtil;
import com.networknt.common.SecretConstants;
import com.networknt.config.Config;
import com.networknt.exception.ClientException;
import com.networknt.httpstring.HttpStringConstants;
import com.networknt.server.ServerConfig;
import com.networknt.utility.Constants;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.IoUtils;
import org.xnio.OptionMap;

import javax.net.ssl.*;
import java.io.InputStream;
import java.net.URI;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static com.networknt.server.Server.TRUST_ALL_CERTS;

public class SAMLTokenTest {
    static final Logger logger = LoggerFactory.getLogger(SAMLTokenTest.class);
    public static final String CONFIG_NAME = "server";
    public static final String CONFIG_SECRET = "secret";

    static Undertow server1 = null;
    static Undertow server2 = null;
    static Undertow server3 = null;
    @ClassRule
    public static TestServer server = TestServer.getInstance();
    public static ServerConfig config = (ServerConfig) Config.getInstance().getJsonObjectConfig(CONFIG_NAME, ServerConfig.class);
    public static Map<String, Object> secret = DecryptUtil.decryptMap(Config.getInstance().getJsonMapConfig(CONFIG_SECRET));
    static SSLContext sslContext = createSSLContext();

    static final boolean enableHttp2 = server.getServerConfig().isEnableHttp2();
    static final boolean enableHttps = server.getServerConfig().isEnableHttps();
    static final int httpPort = server.getServerConfig().getHttpPort();
    static final int httpsPort = server.getServerConfig().getHttpsPort();
    static final String url = enableHttp2 || enableHttps ? "https://localhost:" + httpsPort : "http://localhost:" + httpPort;
    // Two long lived SamlAssertions to verify the saml cache change.
    static final String SAMLAssertion1 = "PD94bWwgdmVyc2lvbj0iMS4wIj8+DQo8c2FtbHA6UmVzcG9uc2UgeG1sbnM6c2FtbHA9InVybjpvYXNpczpuYW1lczp0YzpTQU1MOjIuMDpwcm90b2NvbCIgeG1sbnM6c2FtbD0idXJuOm9hc2lzOm5hbWVzOnRjOlNBTUw6Mi4wOmFzc2VydGlvbiIgSUQ9InBmeDA3Y2UzOTNhLWYwZjAtOTQ5YS0yNjBlLWNkYzA2NWYwYjhlOCIgVmVyc2lvbj0iMi4wIiBJc3N1ZUluc3RhbnQ9IjIwMTQtMDctMTdUMDE6MDE6NDhaIiBEZXN0aW5hdGlvbj0iaHR0cDovL3NwLmV4YW1wbGUuY29tL2RlbW8xL2luZGV4LnBocD9hY3MiIEluUmVzcG9uc2VUbz0iT05FTE9HSU5fNGZlZTNiMDQ2Mzk1YzRlNzUxMDExZTk3Zjg5MDBiNTI3M2Q1NjY4NSI+DQogIDxzYW1sOklzc3Vlcj5odHRwOi8vaWRwLmV4YW1wbGUuY29tL21ldGFkYXRhLnBocDwvc2FtbDpJc3N1ZXI+PGRzOlNpZ25hdHVyZSB4bWxuczpkcz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC8wOS94bWxkc2lnIyI+DQogIDxkczpTaWduZWRJbmZvPjxkczpDYW5vbmljYWxpemF0aW9uTWV0aG9kIEFsZ29yaXRobT0iaHR0cDovL3d3dy53My5vcmcvMjAwMS8xMC94bWwtZXhjLWMxNG4jIi8+DQogICAgPGRzOlNpZ25hdHVyZU1ldGhvZCBBbGdvcml0aG09Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvMDkveG1sZHNpZyNyc2Etc2hhMSIvPg0KICA8ZHM6UmVmZXJlbmNlIFVSST0iI3BmeDA3Y2UzOTNhLWYwZjAtOTQ5YS0yNjBlLWNkYzA2NWYwYjhlOCI+PGRzOlRyYW5zZm9ybXM+PGRzOlRyYW5zZm9ybSBBbGdvcml0aG09Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvMDkveG1sZHNpZyNlbnZlbG9wZWQtc2lnbmF0dXJlIi8+PGRzOlRyYW5zZm9ybSBBbGdvcml0aG09Imh0dHA6Ly93d3cudzMub3JnLzIwMDEvMTAveG1sLWV4Yy1jMTRuIyIvPjwvZHM6VHJhbnNmb3Jtcz48ZHM6RGlnZXN0TWV0aG9kIEFsZ29yaXRobT0iaHR0cDovL3d3dy53My5vcmcvMjAwMC8wOS94bWxkc2lnI3NoYTEiLz48ZHM6RGlnZXN0VmFsdWU+aXlDcHNySk0vdkQzUHM5bCtJVWRieUIyUzlZPTwvZHM6RGlnZXN0VmFsdWU+PC9kczpSZWZlcmVuY2U+PC9kczpTaWduZWRJbmZvPjxkczpTaWduYXR1cmVWYWx1ZT5GWGFsQ055V3EvdFRkQXhYNXRHYmtNTEtucnA5U3cwRndzeE5JRW5KUXVtRUpaRG9mWUJodk1zbXJ0b1FGZ0poM0F0Sm9ERVhDakdWbWd6VTJCN25SVzJwYUpRZEkxQlB1NGhSZWZHR1BkQWtueFg1dHFqSWdCTXJZUFZHbEI0TG56bWZBdzhuR2MraWJvNEtjY0ZyUmQwUEVmcVMzQmh1NWhha0JUcmo0ZXozSkhqdTFNMjBUOHAzaHRZS0xIenJZTjdCSkxSUlo0eU1FeXZIaUpNM2t6NWltTURQaXNsbGRnb3lUVUd0TGV5RGZFeHJjRWcvY21pL2tPOFJqQ3RXUVFDSFBuUThjQWNUSjh6eEd3bkQvK0RIKzZycVhYaENUckdjL3U2TTFXSUU5V1pXVjBtelFjditUdnl3WUc5Nm44c2JjQ21VT1lqWDNyMjVvUDRIa0E9PTwvZHM6U2lnbmF0dXJlVmFsdWU+DQo8ZHM6S2V5SW5mbz48ZHM6WDUwOURhdGE+PGRzOlg1MDlDZXJ0aWZpY2F0ZT5NSUlEVXpDQ0FqdWdBd0lCQWdJSkFJZ3crQk5yODlBL01BMEdDU3FHU0liM0RRRUJDd1VBTUJzeEdUQVhCZ05WQkFNVEVITnpaM05wWjI0dVkybGlZeTVqYjIwd0hoY05NVGd3T1RFME1UY3dNalExV2hjTk1qQXdPVEV6TVRjd01qUTFXakJWTVFzd0NRWURWUVFHRXdKRFFURUxNQWtHQTFVRUNCTUNUMDR4RURBT0JnTlZCQWNUQjFSdmNtOXVkRzh4RFRBTEJnTlZCQW9UQkdOcFltTXhHREFXQmdOVkJBTVREMnhwWjJoMGNtOTFkR1Z5ZEdWemREQ0NBU0l3RFFZSktvWklodmNOQVFFQkJRQURnZ0VQQURDQ0FRb0NnZ0VCQUpYZDlPNmRnQnZrYlVxS1RBNVlhYXhhdE4vczJXQ2kvZEllbWNFQ0liWDIxR1BvdjlRcXN4M0JhQ0F0blFFZms5aWxMRkdnOGs3UlNUWlFrVDBxbUhCdC9YSXpXcFN1VG8zNEU2SnhmczZIeXRqVFpVdWNwKzhNS2lnQ0dkcUlLWHgvcURPWFVKeEFOdGdFejRhd0xxTGtTeU8ycUsyN1VKQ2ZtV2hVWW1hV0FiQnQ1RHdvSjNQdDVKdmZueUR3RFJEWTg1bTFUOGtucEdsQTMxc2RMRzdTMVZyUlFkRFU1TmlLT0lMdHR5YWEyZTlCdFdraTVEdFdkOGcwWUM1bUJLend3UTQ5Uzh3aTJDa1lMWndFenExbm5aK3hnYXFuVmRORjJHdU5mUVVTL3ZJTlBPNy9wYVlMQlJJWmJVNEFIVXZUK09CcmdLaTJDbE8ydUhoTlVBOENBd0VBQWFOZ01GNHdEQVlEVlIwVEFRSC9CQUl3QURBT0JnTlZIUThCQWY4RUJBTUNCZUF3SFFZRFZSME9CQllFRk9RcHhTTE1JY0J1bGhkOGZISVFMT2hJK2RLS01COEdBMVVkSXdRWU1CYUFGUDNCbGkvYkdoQXNiaVVLeHNaMlROUUZNN2FpTUEwR0NTcUdTSWIzRFFFQkN3VUFBNElCQVFCVHBDelpZTno3N1RpYXJla01ZaCtBa3NOWmN6VGtrSEdPbjF0TkxpUGxKSlFXQ0RwcHpBN0dCQS8wUnQ4dWpmRDJzVlZSRE9taElMOUMvNjMyZ1ZqSmt6cUhWNkRRYitaMGgyWTBTUGYycGFJUDlKVGtWcVZnR3BRaFUyZmhMVHYwREl4K1kwaHhhQVIwdlkzUHFpaFBpNzJLaTNXWWV1a1g2RGhEVFgvWmhQMkk1eHNFK3JCb0cwVXNoWGdTUU1tMGx4STZSSWJDMFBNQmhQTmtvZGYwKzhwY3cydTA0YklPM0VMdXdWbExrblNpVmdqQ0M1bzR2YnFRYzRCSjBoaDNaU2d1L1BlT0YvSWlYN2EzOW5zNFl5T2k3eGdyVW9uSHpxQlo1OCtCTjNQQmVZZElQZGlJSUZmV3VLeDVMWUVqbXVUK2VUUURKVVlYNlU1REFESWc8L2RzOlg1MDlDZXJ0aWZpY2F0ZT48L2RzOlg1MDlEYXRhPjwvZHM6S2V5SW5mbz48L2RzOlNpZ25hdHVyZT4NCiAgPHNhbWxwOlN0YXR1cz4NCiAgICA8c2FtbHA6U3RhdHVzQ29kZSBWYWx1ZT0idXJuOm9hc2lzOm5hbWVzOnRjOlNBTUw6Mi4wOnN0YXR1czpTdWNjZXNzIi8+DQogIDwvc2FtbHA6U3RhdHVzPg0KICA8c2FtbDpBc3NlcnRpb24geG1sbnM6eHNpPSJodHRwOi8vd3d3LnczLm9yZy8yMDAxL1hNTFNjaGVtYS1pbnN0YW5jZSIgeG1sbnM6eHM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDEvWE1MU2NoZW1hIiBJRD0iX2Q3MWEzYThlOWZjYzQ1YzllOWQyNDhlZjcwNDkzOTNmYzhmMDRlNWY3NSIgVmVyc2lvbj0iMi4wIiBJc3N1ZUluc3RhbnQ9IjIwMTQtMDctMTdUMDE6MDE6NDhaIj4NCiAgICA8c2FtbDpJc3N1ZXI+aHR0cDovL2lkcC5leGFtcGxlLmNvbS9tZXRhZGF0YS5waHA8L3NhbWw6SXNzdWVyPg0KICAgIDxzYW1sOlN1YmplY3Q+DQogICAgICA8c2FtbDpOYW1lSUQgU1BOYW1lUXVhbGlmaWVyPSJodHRwOi8vc3AuZXhhbXBsZS5jb20vZGVtbzEvbWV0YWRhdGEucGhwIiBGb3JtYXQ9InVybjpvYXNpczpuYW1lczp0YzpTQU1MOjIuMDpuYW1laWQtZm9ybWF0OnRyYW5zaWVudCI+X2NlM2QyOTQ4YjRjZjIwMTQ2ZGVlMGEwYjNkZDZmNjliNmNmODZmNjJkNzwvc2FtbDpOYW1lSUQ+DQogICAgICA8c2FtbDpTdWJqZWN0Q29uZmlybWF0aW9uIE1ldGhvZD0idXJuOm9hc2lzOm5hbWVzOnRjOlNBTUw6Mi4wOmNtOmJlYXJlciI+DQogICAgICAgIDxzYW1sOlN1YmplY3RDb25maXJtYXRpb25EYXRhIE5vdE9uT3JBZnRlcj0iMjAyNC0wMS0xOFQwNjoyMTo0OFoiIFJlY2lwaWVudD0iaHR0cDovL3NwLmV4YW1wbGUuY29tL2RlbW8xL2luZGV4LnBocD9hY3MiIEluUmVzcG9uc2VUbz0iT05FTE9HSU5fNGZlZTNiMDQ2Mzk1YzRlNzUxMDExZTk3Zjg5MDBiNTI3M2Q1NjY4NSIvPg0KICAgICAgPC9zYW1sOlN1YmplY3RDb25maXJtYXRpb24+DQogICAgPC9zYW1sOlN1YmplY3Q+DQogICAgPHNhbWw6Q29uZGl0aW9ucyBOb3RCZWZvcmU9IjIwMTQtMDctMTdUMDE6MDE6MThaIiBOb3RPbk9yQWZ0ZXI9IjIwMjQtMDEtMThUMDY6MjE6NDhaIj4NCiAgICAgIDxzYW1sOkF1ZGllbmNlUmVzdHJpY3Rpb24+DQogICAgICAgIDxzYW1sOkF1ZGllbmNlPmh0dHA6Ly9zcC5leGFtcGxlLmNvbS9kZW1vMS9tZXRhZGF0YS5waHA8L3NhbWw6QXVkaWVuY2U+DQogICAgICA8L3NhbWw6QXVkaWVuY2VSZXN0cmljdGlvbj4NCiAgICA8L3NhbWw6Q29uZGl0aW9ucz4NCiAgICA8c2FtbDpBdXRoblN0YXRlbWVudCBBdXRobkluc3RhbnQ9IjIwMTQtMDctMTdUMDE6MDE6NDhaIiBTZXNzaW9uTm90T25PckFmdGVyPSIyMDI0LTA3LTE3VDA5OjAxOjQ4WiIgU2Vzc2lvbkluZGV4PSJfYmU5OTY3YWJkOTA0ZGRjYWUzYzBlYjQxODlhZGJlM2Y3MWUzMjdjZjkzIj4NCiAgICAgIDxzYW1sOkF1dGhuQ29udGV4dD4NCiAgICAgICAgPHNhbWw6QXV0aG5Db250ZXh0Q2xhc3NSZWY+dXJuOm9hc2lzOm5hbWVzOnRjOlNBTUw6Mi4wOmFjOmNsYXNzZXM6UGFzc3dvcmQ8L3NhbWw6QXV0aG5Db250ZXh0Q2xhc3NSZWY+DQogICAgICA8L3NhbWw6QXV0aG5Db250ZXh0Pg0KICAgIDwvc2FtbDpBdXRoblN0YXRlbWVudD4NCiAgICA8c2FtbDpBdHRyaWJ1dGVTdGF0ZW1lbnQ+DQogICAgICA8c2FtbDpBdHRyaWJ1dGUgTmFtZT0idWlkIiBOYW1lRm9ybWF0PSJ1cm46b2FzaXM6bmFtZXM6dGM6U0FNTDoyLjA6YXR0cm5hbWUtZm9ybWF0OmJhc2ljIj4NCiAgICAgICAgPHNhbWw6QXR0cmlidXRlVmFsdWUgeHNpOnR5cGU9InhzOnN0cmluZyI+dGVzdDwvc2FtbDpBdHRyaWJ1dGVWYWx1ZT4NCiAgICAgIDwvc2FtbDpBdHRyaWJ1dGU+DQogICAgICA8c2FtbDpBdHRyaWJ1dGUgTmFtZT0ibWFpbCIgTmFtZUZvcm1hdD0idXJuOm9hc2lzOm5hbWVzOnRjOlNBTUw6Mi4wOmF0dHJuYW1lLWZvcm1hdDpiYXNpYyI+DQogICAgICAgIDxzYW1sOkF0dHJpYnV0ZVZhbHVlIHhzaTp0eXBlPSJ4czpzdHJpbmciPnRlc3RAZXhhbXBsZS5jb208L3NhbWw6QXR0cmlidXRlVmFsdWU+DQogICAgICA8L3NhbWw6QXR0cmlidXRlPg0KICAgICAgPHNhbWw6QXR0cmlidXRlIE5hbWU9ImVkdVBlcnNvbkFmZmlsaWF0aW9uIiBOYW1lRm9ybWF0PSJ1cm46b2FzaXM6bmFtZXM6dGM6U0FNTDoyLjA6YXR0cm5hbWUtZm9ybWF0OmJhc2ljIj4NCiAgICAgICAgPHNhbWw6QXR0cmlidXRlVmFsdWUgeHNpOnR5cGU9InhzOnN0cmluZyI+dXNlcnM8L3NhbWw6QXR0cmlidXRlVmFsdWU+DQogICAgICAgIDxzYW1sOkF0dHJpYnV0ZVZhbHVlIHhzaTp0eXBlPSJ4czpzdHJpbmciPmV4YW1wbGVyb2xlMTwvc2FtbDpBdHRyaWJ1dGVWYWx1ZT4NCiAgICAgIDwvc2FtbDpBdHRyaWJ1dGU+DQogICAgPC9zYW1sOkF0dHJpYnV0ZVN0YXRlbWVudD4NCiAgPC9zYW1sOkFzc2VydGlvbj4NCjwvc2FtbHA6UmVzcG9uc2U+";
    static final String SAMLAssertion2 = "PHNhbWwyOkFzc2VydGlvbiBWZXJzaW9uPSIyLjAiIElEPSJTYW1sQXNzZXJ0aW9uLTQzYTkxZTAzMzgzNDU5ODQ2YjkxNmI0OWQ5ZWI4MzNjIiBJc3N1ZUluc3RhbnQ9IjIwMTgtMTEtMDdUMjE6MTA6MDIuMzAxWiIgeG1sbnM6c2FtbDI9InVybjpvYXNpczpuYW1lczp0YzpTQU1MOjIuMDphc3NlcnRpb24iPjxzYW1sMjpJc3N1ZXI+bGlnaHRyb3V0ZXJ0ZXN0PC9zYW1sMjpJc3N1ZXI+PGRzOlNpZ25hdHVyZSB4bWxuczpkcz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC8wOS94bWxkc2lnIyI+PGRzOlNpZ25lZEluZm8+PGRzOkNhbm9uaWNhbGl6YXRpb25NZXRob2QgQWxnb3JpdGhtPSJodHRwOi8vd3d3LnczLm9yZy8yMDAxLzEwL3htbC1leGMtYzE0biMiLz48ZHM6U2lnbmF0dXJlTWV0aG9kIEFsZ29yaXRobT0iaHR0cDovL3d3dy53My5vcmcvMjAwMC8wOS94bWxkc2lnI3JzYS1zaGExIi8+PGRzOlJlZmVyZW5jZSBVUkk9IiNTYW1sQXNzZXJ0aW9uLTQzYTkxZTAzMzgzNDU5ODQ2YjkxNmI0OWQ5ZWI4MzNjIj48ZHM6VHJhbnNmb3Jtcz48ZHM6VHJhbnNmb3JtIEFsZ29yaXRobT0iaHR0cDovL3d3dy53My5vcmcvMjAwMC8wOS94bWxkc2lnI2VudmVsb3BlZC1zaWduYXR1cmUiLz48ZHM6VHJhbnNmb3JtIEFsZ29yaXRobT0iaHR0cDovL3d3dy53My5vcmcvMjAwMS8xMC94bWwtZXhjLWMxNG4jIi8+PC9kczpUcmFuc2Zvcm1zPjxkczpEaWdlc3RNZXRob2QgQWxnb3JpdGhtPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwLzA5L3htbGRzaWcjc2hhMSIvPjxkczpEaWdlc3RWYWx1ZT5MVFUzYUZwZG5UL3ZFdGJwd09CbHlTNG5KM0k9PC9kczpEaWdlc3RWYWx1ZT48L2RzOlJlZmVyZW5jZT48L2RzOlNpZ25lZEluZm8+PGRzOlNpZ25hdHVyZVZhbHVlPk9BZlBicFdLaVRyaW9KM3R5VnNrYytaZHRtYk5IVURybzl2U29BSTZCTGUyd1dGMXduNGx1dXgzNHhZMlZPVmhWQWoxa09QT1FJQ25jOEl2ZTFHTlJ5Vjd1cTIzSmdtVGlFdWx5dVJCaWJrZ0MzOUdRRDZRZ0U5T3N4YjlucVAxeUJId29DMlBDb2ozUElNYjhvTWFqNytZZk5KQ0czdzU1eTlocGZiM0c0TjFXK05MWE83TmkvczJGaXpLZVd6MFNlZnlCT2xiY0xKSFkxU2xIZmhiaHlLVzJkdXo0dUsyYUM1NFhQWERVUEdYa3VESkRVbS9Cb3lPMWNxdXdZa3l6UTk2YVhVaWVORW15S3ZUTnE2OEE2dWNlckpDZmxZaDNxMEtuRFduNUI5aXZuVWR6Z05QZGxsTmM2MkIwaVFHWnNyZTcyWjE1LzNvTWh6clhjWjRXZz09PC9kczpTaWduYXR1cmVWYWx1ZT48S2V5SW5mbyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC8wOS94bWxkc2lnIyI+PFg1MDlEYXRhPjxYNTA5U3ViamVjdE5hbWU+Q049bGlnaHRyb3V0ZXJ0ZXN0LE89Y2liYyxMPVRvcm9udG8sU1Q9T04sQz1DQTwvWDUwOVN1YmplY3ROYW1lPjxYNTA5Q2VydGlmaWNhdGU+TUlJRFV6Q0NBanVnQXdJQkFnSUpBSWd3K0JOcjg5QS9NQTBHQ1NxR1NJYjNEUUVCQ3dVQU1Cc3hHVEFYQmdOVkJBTVRFSE56WjNOcFoyNHVZMmxpWXk1amIyMHdIaGNOTVRnd09URTBNVGN3TWpRMVdoY05NakF3T1RFek1UY3dNalExV2pCVk1Rc3dDUVlEVlFRR0V3SkRRVEVMTUFrR0ExVUVDQk1DVDA0eEVEQU9CZ05WQkFjVEIxUnZjbTl1ZEc4eERUQUxCZ05WQkFvVEJHTnBZbU14R0RBV0JnTlZCQU1URDJ4cFoyaDBjbTkxZEdWeWRHVnpkRENDQVNJd0RRWUpLb1pJaHZjTkFRRUJCUUFEZ2dFUEFEQ0NBUW9DZ2dFQkFKWGQ5TzZkZ0J2a2JVcUtUQTVZYWF4YXROL3MyV0NpL2RJZW1jRUNJYlgyMUdQb3Y5UXFzeDNCYUNBdG5RRWZrOWlsTEZHZzhrN1JTVFpRa1QwcW1IQnQvWEl6V3BTdVRvMzRFNkp4ZnM2SHl0alRaVXVjcCs4TUtpZ0NHZHFJS1h4L3FET1hVSnhBTnRnRXo0YXdMcUxrU3lPMnFLMjdVSkNmbVdoVVltYVdBYkJ0NUR3b0ozUHQ1SnZmbnlEd0RSRFk4NW0xVDhrbnBHbEEzMXNkTEc3UzFWclJRZERVNU5pS09JTHR0eWFhMmU5QnRXa2k1RHRXZDhnMFlDNW1CS3p3d1E0OVM4d2kyQ2tZTFp3RXpxMW5uWit4Z2FxblZkTkYyR3VOZlFVUy92SU5QTzcvcGFZTEJSSVpiVTRBSFV2VCtPQnJnS2kyQ2xPMnVIaE5VQThDQXdFQUFhTmdNRjR3REFZRFZSMFRBUUgvQkFJd0FEQU9CZ05WSFE4QkFmOEVCQU1DQmVBd0hRWURWUjBPQkJZRUZPUXB4U0xNSWNCdWxoZDhmSElRTE9oSStkS0tNQjhHQTFVZEl3UVlNQmFBRlAzQmxpL2JHaEFzYmlVS3hzWjJUTlFGTTdhaU1BMEdDU3FHU0liM0RRRUJDd1VBQTRJQkFRQlRwQ3paWU56NzdUaWFyZWtNWWgrQWtzTlpjelRra0hHT24xdE5MaVBsSkpRV0NEcHB6QTdHQkEvMFJ0OHVqZkQyc1ZWUkRPbWhJTDlDLzYzMmdWakprenFIVjZEUWIrWjBoMlkwU1BmMnBhSVA5SlRrVnFWZ0dwUWhVMmZoTFR2MERJeCtZMGh4YUFSMHZZM1BxaWhQaTcyS2kzV1lldWtYNkRoRFRYL1poUDJJNXhzRStyQm9HMFVzaFhnU1FNbTBseEk2UkliQzBQTUJoUE5rb2RmMCs4cGN3MnUwNGJJTzNFTHV3VmxMa25TaVZnakNDNW80dmJxUWM0QkowaGgzWlNndS9QZU9GL0lpWDdhMzluczRZeU9pN3hnclVvbkh6cUJaNTgrQk4zUEJlWWRJUGRpSUlGZld1S3g1TFlFam11VCtlVFFESlVZWDZVNURBRElnPC9YNTA5Q2VydGlmaWNhdGU+PC9YNTA5RGF0YT48L0tleUluZm8+PC9kczpTaWduYXR1cmU+PHNhbWwyOlN1YmplY3Q+PHNhbWwyOk5hbWVJRCBGb3JtYXQ9InVybjpvYXNpczpuYW1lczp0YzpTQU1MOjEuMTpuYW1laWQtZm9ybWF0OnVuc3BlY2lmaWVkIiBOYW1lUXVhbGlmaWVyPSIiPmFkbWluPC9zYW1sMjpOYW1lSUQ+PHNhbWwyOlN1YmplY3RDb25maXJtYXRpb24gTWV0aG9kPSJ1cm46b2FzaXM6bmFtZXM6dGM6U0FNTDoyLjA6Y206YmVhcmVyIj48c2FtbDI6U3ViamVjdENvbmZpcm1hdGlvbkRhdGEgUmVjaXBpZW50PSJodHRwczovL3NpdC5vYXMuY2liYy5jb20iLz48L3NhbWwyOlN1YmplY3RDb25maXJtYXRpb24+PC9zYW1sMjpTdWJqZWN0PjxzYW1sMjpDb25kaXRpb25zIE5vdEJlZm9yZT0iMjAxOC0xMS0wN1QyMTowODowMi4zMDFaIiBOb3RPbk9yQWZ0ZXI9IjIwMTgtMTEtMDdUMjE6MTU6MDIuMzAxWiI+PHNhbWwyOkF1ZGllbmNlUmVzdHJpY3Rpb24+PHNhbWwyOkF1ZGllbmNlPmh0dHBzOi8vc2l0Lm9hcy5jaWJjLmNvbTwvc2FtbDI6QXVkaWVuY2U+PC9zYW1sMjpBdWRpZW5jZVJlc3RyaWN0aW9uPjwvc2FtbDI6Q29uZGl0aW9ucz48c2FtbDI6QXV0aG5TdGF0ZW1lbnQgQXV0aG5JbnN0YW50PSIyMDE4LTExLTA3VDIxOjEwOjAyLjMwMVoiPjxzYW1sMjpTdWJqZWN0TG9jYWxpdHkgQWRkcmVzcz0iMTAuODMuMi4xMzUiLz48c2FtbDI6QXV0aG5Db250ZXh0PjxzYW1sMjpBdXRobkNvbnRleHRDbGFzc1JlZj51cm46b2FzaXM6bmFtZXM6dGM6U0FNTDoyLjA6YWM6Y2xhc3NlczpQYXNzd29yZDwvc2FtbDI6QXV0aG5Db250ZXh0Q2xhc3NSZWY+PC9zYW1sMjpBdXRobkNvbnRleHQ+PC9zYW1sMjpBdXRoblN0YXRlbWVudD48L3NhbWwyOkFzc2VydGlvbj4=";
    static final String JWTAssertion = "eyJraWQiOiIxMDAiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJ1cm46Y29tOm5ldHdvcmtudDpvYXV0aDI6djEiLCJhdWQiOiJ1cm46Y29tLm5ldHdvcmtudCIsImV4cCI6MTg1MjMxNDQzMywianRpIjoiT1pQaXhwQ0FVMmtEQ1AzVC1zTzJ2dyIsImlhdCI6MTUzNjk1NDQzMywibmJmIjoxNTM2OTU0MzEzLCJ2ZXJzaW9uIjoiMS4wIiwidXNlcl9pZCI6InN0ZXZlIiwidXNlcl90eXBlIjoiRU1QTE9ZRUUiLCJjbGllbnRfaWQiOiJmN2Q0MjM0OC1jNjQ3LTRlZmItYTUyZC00YzU3ODc0MjFlNzIiLCJjb25zdW1lcl9hcHBsaWNhdGlvbl9pZCI6IjM2MSIsInJlcXVlc3RfdHJhbnNpdCI6IjY3In0.j7O2jEjdXxRz7xum-lUJf7S40sk8ifjOEAMkvzpanLReC3M0vGH0ZYNTP0wvxsg9VuASGayiqDrLluMG4un1uMtMLzbPVELclm89cAMJnx_62Mco1EPzB79vlJOkbLPFxiyxIoDS9ChKX5aDBMsUgYEQU1-1UcLW_-FtvVUDcxxyN9vQE0ygMnq7oADOMqMHlISVQpFRjAO8bLGLI4QiKe17ufgPKCFHOMPxRaEx-fR-APH3hf7nNUGHHiIG9TdxlLGAlZAZY2WNjzVmb76I428_3BnDc1h03XQo6AhJNSLHpeNRxehNrXeQEQQtri46cDGkHDVei972Mn7go1hcFw";

    @BeforeClass
    public static void setUp() {
        if (server1 == null) {
            logger.info("starting server1");
            server1 = Undertow.builder()
                    .addHttpsListener(8081, "localhost", sslContext)
                    .setHandler(new HttpHandler() {
                        @Override
                        public void handleRequest(HttpServerExchange exchange) throws Exception {
                            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                            exchange.getResponseSender().send("Server1");
                        }
                    })
                    .build();

            server1.start();
        }

        if (server2 == null) {
            logger.info("starting server2");
            server2 = Undertow.builder()
                    .addHttpsListener(8082, "localhost", sslContext)
                    .setHandler(new HttpHandler() {
                        @Override
                        public void handleRequest(HttpServerExchange exchange) throws Exception {
                            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                            exchange.getResponseSender().send("Server2");
                        }
                    })
                    .build();

            server2.start();
        }

        if (server3 == null) {
            logger.info("starting server3");
            server3 = Undertow.builder()
                    .addHttpsListener(8083, "localhost", sslContext)
                    .setHandler(new HttpHandler() {
                        @Override
                        public void handleRequest(HttpServerExchange exchange) throws Exception {
                            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                            exchange.getResponseSender().send("Server3");
                        }
                    })
                    .build();

            server3.start();
        }
    }

    @AfterClass
    public static void tearDown() throws Exception {
        if (server1 != null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {

            }
            server1.stop();
            logger.info("The server1 is stopped.");
        }
        if (server2 != null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {

            }
            server2.stop();
            logger.info("The server2 is stopped.");
        }
        if (server3 != null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {

            }
            server3.stop();
            logger.info("The server3 is stopped.");
        }
    }


    @Test
    public void testGet() throws Exception {
        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(3);
        final ClientConnection connection;
        try {
            connection = client.connect(new URI(url), Http2Client.WORKER, Http2Client.SSL, Http2Client.BUFFER_POOL, enableHttp2 ? OptionMap.create(UndertowOptions.ENABLE_HTTP2, true) : OptionMap.EMPTY).get();
        } catch (Exception e) {
            throw new ClientException(e);
        }

        final List<AtomicReference<ClientResponse>> references = new CopyOnWriteArrayList<>();
        int count;
        try {
            connection.getIoThread().execute(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 3; i++) {
                        AtomicReference<ClientResponse> reference = new AtomicReference<>();
                        references.add(i, reference);
                        final ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath("/v2/address");
                        request.getRequestHeaders().put(HttpStringConstants.SERVICE_ID, "com.networknt.test-1.0.0");
                        if (i == 2) {
                            request.getRequestHeaders().put(new HttpString("assertion"), SAMLAssertion2);
                        } else {
                            request.getRequestHeaders().put(new HttpString("assertion"), SAMLAssertion1);
                        }
                        request.getRequestHeaders().put(new HttpString("client_assertion"), JWTAssertion);
                        connection.sendRequest(request, client.createClientCallback(reference, latch));
                    }
                }

            });

            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {
            IoUtils.safeClose(connection);
        }
        for (final AtomicReference<ClientResponse> reference : references) {
            String body = reference.get().getAttachment(Http2Client.RESPONSE_BODY);
            Assert.assertTrue(body.contains("Server"));
        }
    }

    private static SSLContext createSSLContext() throws RuntimeException {
        try {
            KeyManager[] keyManagers = buildKeyManagers(loadKeyStore(), ((String) secret.get(SecretConstants.SERVER_KEY_PASS)).toCharArray());
            TrustManager[] trustManagers;
            if (config.isEnableTwoWayTls()) {
                trustManagers = buildTrustManagers(loadTrustStore());
            } else {
                trustManagers = buildTrustManagers(null);
            }

            SSLContext sslContext;
            sslContext = SSLContext.getInstance("TLSv1");
            sslContext.init(keyManagers, trustManagers, null);
            return sslContext;
        } catch (Exception e) {
            logger.error("Unable to create SSLContext", e);
            throw new RuntimeException("Unable to create SSLContext", e);
        }
    }

    private static KeyStore loadKeyStore() {
        String name = config.getKeystoreName();
        try (InputStream stream = Config.getInstance().getInputStreamFromFile(name)) {
            KeyStore loadedKeystore = KeyStore.getInstance("JKS");
            loadedKeystore.load(stream, ((String) secret.get(SecretConstants.SERVER_KEYSTORE_PASS)).toCharArray());
            return loadedKeystore;
        } catch (Exception e) {
            logger.error("Unable to load keystore " + name, e);
            throw new RuntimeException("Unable to load keystore " + name, e);
        }
    }

    protected static KeyStore loadTrustStore() {
        String name = config.getTruststoreName();
        try (InputStream stream = Config.getInstance().getInputStreamFromFile(name)) {
            KeyStore loadedKeystore = KeyStore.getInstance("JKS");
            loadedKeystore.load(stream, ((String) secret.get(SecretConstants.SERVER_TRUSTSTORE_PASS)).toCharArray());
            return loadedKeystore;
        } catch (Exception e) {
            logger.error("Unable to load truststore " + name, e);
            throw new RuntimeException("Unable to load truststore " + name, e);
        }
    }

    private static TrustManager[] buildTrustManagers(final KeyStore trustStore) {
        TrustManager[] trustManagers = null;
        if (trustStore == null) {
            try {
                TrustManagerFactory trustManagerFactory = TrustManagerFactory
                        .getInstance(KeyManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(trustStore);
                trustManagers = trustManagerFactory.getTrustManagers();
            } catch (NoSuchAlgorithmException | KeyStoreException e) {
                logger.error("Unable to initialise TrustManager[]", e);
                throw new RuntimeException("Unable to initialise TrustManager[]", e);
            }
        } else {
            trustManagers = TRUST_ALL_CERTS;
        }
        return trustManagers;
    }

    private static KeyManager[] buildKeyManagers(final KeyStore keyStore, char[] keyPass) {
        KeyManager[] keyManagers;
        try {
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory
                    .getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, keyPass);
            keyManagers = keyManagerFactory.getKeyManagers();
        } catch (NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException e) {
            logger.error("Unable to initialise KeyManager[]", e);
            throw new RuntimeException("Unable to initialise KeyManager[]", e);
        }
        return keyManagers;
    }

}
