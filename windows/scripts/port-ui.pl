#!/usr/bin/perl
# Mechanical Android->Compose Desktop port of one UI file (win-audio). Usage: port-ui.pl in out newPackage
use strict;
my ($in,$out,$pkg)=@ARGV;
local $/; open(F,'<:utf8',$in) or die; my $s=<F>; close F;
$s =~ s/^package .*$/package $pkg/m if $pkg;
my %names; 
$s =~ s/(?:com\.example\.)?R\.(string|drawable|font)\.(\w+)/$names{$2}=1; "Res.$1.$2"/ge;
$s =~ s/(?:androidx\.compose\.ui\.res\.)?stringResource\(\s*Res\.string\.(\w+)\s*,/tr(Res.string.$1,/g;
$s =~ s/androidx\.compose\.ui\.res\.stringResource\(/stringResource(/g;
$s =~ s/^import com\.example\.R\s*\n//m;
$s =~ s/^import androidx\.compose\.ui\.res\.stringResource\s*\n/import org.jetbrains.compose.resources.stringResource\n/m;
$s =~ s/^import androidx\.compose\.ui\.res\.painterResource\s*\n/import org.jetbrains.compose.resources.painterResource\n/m;
$s =~ s/^import androidx\.activity\.compose\.BackHandler\s*\n/import com.example.windows.base.BackHandler\n/m;
my $imp = "import com.example.windows.res.Res\nimport com.example.windows.res.tr\n";
$imp .= join("", map {"import com.example.windows.res.$_\n"} sort keys %names);
$s =~ s/^(import )/$imp$1/m;
open(O,'>:utf8',$out) or die; print O $s; close O;
