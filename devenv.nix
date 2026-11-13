# https://devenv.sh/reference/options/
# Postgres and Redis stay in docker-compose.yml: the tests need Docker for Testcontainers anyway.
{ pkgs, ... }:
{
  languages.java = {
    enable = true;
    jdk.package = pkgs.jdk25;
  };

  # For apps/frontend once it has code:
  # languages.javascript = {
  #   enable = true;
  #   package = pkgs.nodejs_24;
  #   npm.enable = true;
  # };
}
