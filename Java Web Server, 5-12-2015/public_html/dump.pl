#!perl

binmode STDOUT;
print "Content-Type: text/plain\r\n\r\n";


print "STDIN: ";
while(<STDIN>) {
	print;
}
print "\r\n\r\n";

for my $var ( sort keys %ENV ) {
	my $value = $ENV{$var};
	print "$var: $value\r\n";
}
