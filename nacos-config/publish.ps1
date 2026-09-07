param(
    [string]$ServerAddr = "http://localhost:8848",
    [string]$Group = "LITESHOP_GROUP",
    [string]$Namespace = "public",
    [string]$Username = "nacos",
    [string]$Password = "123456"
)

$ErrorActionPreference = "Stop"

$baseUrl = $ServerAddr.TrimEnd("/")
if (-not ($baseUrl.StartsWith("http://") -or $baseUrl.StartsWith("https://"))) {
    $baseUrl = "http://$baseUrl"
}
if (-not $baseUrl.EndsWith("/nacos")) {
    $baseUrl = "$baseUrl/nacos"
}

# Nacos 3.x：管理接口(v3/admin)默认开启鉴权，需要先登录获取 accessToken
$headers = @{}
if ($Username -and $Password) {
    $loginResult = Invoke-RestMethod -Method Post -Uri "$baseUrl/v3/auth/user/login" `
        -Body @{ username = $Username; password = $Password }
    $headers["accessToken"] = $loginResult.accessToken
}

$templateFiles = Get-ChildItem -LiteralPath $PSScriptRoot -Filter "*.example.yml" | Sort-Object Name
foreach ($file in $templateFiles) {
    $dataId = $file.Name -replace "\.example(?=\.yml$)", ""
    $content = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8

    # 手动做 UTF-8 URL 编码，避免中文内容在表单提交时乱码
    $fields = [ordered]@{
        dataId      = $dataId
        groupName   = $Group
        namespaceId = $Namespace
        type        = "yaml"
        content     = $content
    }
    $body = ($fields.GetEnumerator() | ForEach-Object {
        "$($_.Key)=$([System.Uri]::EscapeDataString([string]$_.Value))"
    }) -join "&"

    $result = Invoke-RestMethod -Method Post -Uri "$baseUrl/v3/admin/cs/config" `
        -Headers $headers -Body $body `
        -ContentType "application/x-www-form-urlencoded; charset=utf-8"

    if ($result.data -ne $true) {
        throw "发布失败: $dataId => $($result | ConvertTo-Json -Compress)"
    }
    Write-Host "已发布 [$Group] $dataId"
}

Write-Host "`n全部发布完成，可在 Nacos 控制台的「配置管理」中查看。"
