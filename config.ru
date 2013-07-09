require "rubygems"
require "bundler"
 
map "/" do
  server = "Play!" # LOL
  use Rack::Deflater # use GZIP
  use Rack::Static,
    :urls => ["/list","/assets/images", "/assets/javascripts", "/assets/stylesheets", "/pdf"],
    :root => "site",
    :header_rules => [[:all, {"Server" => server}]]
 
 
  run lambda { |env|
    headers = {
      "Content-Type"  => "text/html",
      "Cache-Control" => "public, max-age=86400",
      "Server" => server
    }
    body = File.open("site/index.html", File::RDONLY)
 
    [200, headers, body]
  }
end

map "/list" do
  server = "Play!" # LOL
  use Rack::Deflater # use GZIP
 
  run lambda { |env|
    headers = {
      "Content-Type"  => "text/html",
      "Cache-Control" => "public, max-age=86400",
      "Server" => server
    }
    body = File.open("site/list.html", File::RDONLY)
 
    [200, headers, body]
  }
end