#!/usr/bin/env perl
########################################################################
# Authors: Christopher Henry, Scott Devoid, Paul Frybarger
# Contact email: chenry@mcs.anl.gov
# Development location: Mathematics and Computer Science Division, Argonne National Lab
########################################################################
use strict;
use warnings;
use Getopt::Long::Descriptive;
use Text::Table;
use Bio::KBase::workspace::ScriptHelpers qw(get_ws_client workspace parseObjectMeta parseWorkspaceMeta);

my $serv = get_ws_client();
#Defining globals describing behavior
my $primaryArgs = ["Object ID or Name"];
my $servercommand = "get_object_history";
my $translation = {
	"Object ID or Name" => "id",
	workspace => "workspace"
};
#Defining usage and options
my ($opt, $usage) = describe_options(
    'kbws-history <'.join("> <",@{$primaryArgs}).'> %o',
    [ 'workspace|w=s', 'ID or Name of the workspace to use', {"default" => workspace()} ],
    [ 'showerror|e', 'show any errors in execution',{"default"=>0}],
    [ 'help|h|?', 'Print this usage information' ]
);
if (defined($opt->{help})) {
	print $usage;
    exit;
}

#Processing primary arguments
foreach my $arg (@{$primaryArgs}) {
	$opt->{$arg} = shift @ARGV;
	if (!defined($opt->{$arg})) {
		print $usage;
    	exit;
	}
}
#Instantiating parameters
my $params = {
	      ref => $opt->{workspace} ."/".$opt->{"Object ID or Name"},
	      };


#Calling the server
my $output;
if ($opt->{showerror} == 0){
	eval { $output = $serv->$servercommand($params); };
	if($@) {
		print "Cannot list object history!\n";
		print STDERR $@->{message}."\n";
		if(defined($@->{status_line})) {print STDERR $@->{status_line}."\n" };
		print STDERR "\n";
		exit 1;
	}
} else {
	$output = $serv->$servercommand($params);
}

#Checking output and report results
if (!defined($output)) {
	print "Cannot list object History!\n";
} else {
	#tuple<0obj_id objid, 1obj_name name, 2type_string type,
	#	3timestamp save_date, 4int version, 5username saved_by,
	#	6ws_id wsid, 7ws_name workspace, 8string chsum, 9int size, 10usermeta meta>
	#	object_info;
	my $tbl = [];
	for (my $i=0; $i < @{$output};$i++) {
	    my $r = $output->[$i];
	    push(@{$tbl},[$r->[0],$r->[1],$r->[4],$r->[2],$r->[7],$r->[5],$r->[3],$r->[9]]);
	}
	my $table = Text::Table->new(
		'ID', 'ObjName', 'Vers', 'Type', 'WS','Last_modby','Moddate','Size(bytes)'
		);
	$table->load(reverse(@$tbl));
	
	print $table;
}

exit 0;