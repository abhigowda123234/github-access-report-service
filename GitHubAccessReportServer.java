
import java.io.*;
import java.net.*;
import java.util.*;
import com.sun.net.httpserver.*;

public class GitHubAccessReportServer {

    private static String githubToken = System.getenv("GITHUB_TOKEN");
    private static String org = System.getenv("GITHUB_ORG");

    public static void main(String[] args) throws Exception {

        if(githubToken==null || org==null){
            System.out.println("Please set environment variables GITHUB_TOKEN and GITHUB_ORG");
            return;
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/report", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            try {
                String json = generateReport();
                byte[] resp = json.getBytes("UTF-8");
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, resp.length);
                OutputStream os = exchange.getResponseBody();
                os.write(resp);
                os.close();
            } catch(Exception e){
                String err = "{\"error\":\""+e.getMessage()+"\"}";
                byte[] resp = err.getBytes();
                exchange.sendResponseHeaders(500, resp.length);
                OutputStream os = exchange.getResponseBody();
                os.write(resp);
                os.close();
            }
        });

        server.start();
        System.out.println("Server running at http://localhost:8080/report");
    }

    private static String githubGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Authorization","Bearer "+githubToken);
        conn.setRequestProperty("Accept","application/vnd.github+json");
        conn.setRequestMethod("GET");

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while((line=reader.readLine())!=null){
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }

    private static String generateReport() throws Exception {

        String reposJson = githubGet("https://api.github.com/orgs/"+org+"/repos?per_page=100");
        List<String> repoNames = new ArrayList<String>();

        String[] parts = reposJson.split("\"name\":\"");
        for(int i=1;i<parts.length;i++){
            repoNames.add(parts[i].split("\"")[0]);
        }

        Map<String, List<String>> userRepos = new HashMap<String, List<String>>();

        for(String repo: repoNames){
            String collabJson = githubGet("https://api.github.com/repos/"+org+"/"+repo+"/collaborators?per_page=100");

            String[] users = collabJson.split("\"login\":\"");
            for(int i=1;i<users.length;i++){
                String user = users[i].split("\"")[0];

                if(!userRepos.containsKey(user)){
                    userRepos.put(user,new ArrayList<String>());
                }

                userRepos.get(user).add(repo);
            }
        }

        StringBuilder result = new StringBuilder();
        result.append("{");

        int ucount=0;
        for(String user: userRepos.keySet()){
            if(ucount++>0) result.append(",");
            result.append("\"").append(user).append("\":[");

            List<String> repos=userRepos.get(user);
            for(int i=0;i<repos.size();i++){
                if(i>0) result.append(",");
                result.append("\"").append(repos.get(i)).append("\"");
            }
            result.append("]");
        }

        result.append("}");
        return result.toString();
    }
}
