#!perl

binmode STDOUT;
print "Content-Type: text/plain\r\n\r\n";


my %form;
if( $ENV{'REQUEST_METHOD'} =~ /^POST$/i ) {
	$_ = <STDIN>;
} else {
	$_ = $ENV{'QUERY_STRING'};
}
for (split /&/) {
	my @pair = split /=/;
	$form{$pair[0]} = $pair[1];
}
print "Dear $form{name}, the sum of $form{a} and $form{b} is ", $form{'a'} + $form{'b'}, ".";
