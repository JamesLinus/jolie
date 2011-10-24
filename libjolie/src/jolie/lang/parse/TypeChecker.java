/***************************************************************************
 *   Copyright (C) 2011 by Fabrizio Montesi <famontesi@gmail.com>          *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU Library General Public License as       *
 *   published by the Free Software Foundation; either version 2 of the    *
 *   License, or (at your option) any later version.                       *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU Library General Public     *
 *   License along with this program; if not, write to the                 *
 *   Free Software Foundation, Inc.,                                       *
 *   59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.             *
 *                                                                         *
 *   For details about the authors of this software, see the AUTHORS file. *
 ***************************************************************************/

package jolie.lang.parse;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import jolie.lang.Constants;
import jolie.lang.Constants.ExecutionMode;
import jolie.lang.parse.ast.AddAssignStatement;
import jolie.lang.parse.ast.AndConditionNode;
import jolie.lang.parse.ast.AssignStatement;
import jolie.lang.parse.ast.CompareConditionNode;
import jolie.lang.parse.ast.CompensateStatement;
import jolie.lang.parse.ast.ConstantIntegerExpression;
import jolie.lang.parse.ast.ConstantRealExpression;
import jolie.lang.parse.ast.ConstantStringExpression;
import jolie.lang.parse.ast.CorrelationSetInfo;
import jolie.lang.parse.ast.CorrelationSetInfo.CorrelationVariableInfo;
import jolie.lang.parse.ast.CurrentHandlerStatement;
import jolie.lang.parse.ast.DeepCopyStatement;
import jolie.lang.parse.ast.DefinitionCallStatement;
import jolie.lang.parse.ast.DefinitionNode;
import jolie.lang.parse.ast.DivideAssignStatement;
import jolie.lang.parse.ast.DocumentationComment;
import jolie.lang.parse.ast.EmbeddedServiceNode;
import jolie.lang.parse.ast.ExecutionInfo;
import jolie.lang.parse.ast.ExitStatement;
import jolie.lang.parse.ast.ExpressionConditionNode;
import jolie.lang.parse.ast.ForEachStatement;
import jolie.lang.parse.ast.ForStatement;
import jolie.lang.parse.ast.FreshValueExpressionNode;
import jolie.lang.parse.ast.IfStatement;
import jolie.lang.parse.ast.InputPortInfo;
import jolie.lang.parse.ast.InstallFixedVariableExpressionNode;
import jolie.lang.parse.ast.InstallStatement;
import jolie.lang.parse.ast.InterfaceDefinition;
import jolie.lang.parse.ast.InterfaceExtenderDefinition;
import jolie.lang.parse.ast.IsTypeExpressionNode;
import jolie.lang.parse.ast.LinkInStatement;
import jolie.lang.parse.ast.LinkOutStatement;
import jolie.lang.parse.ast.MultiplyAssignStatement;
import jolie.lang.parse.ast.NDChoiceStatement;
import jolie.lang.parse.ast.NotConditionNode;
import jolie.lang.parse.ast.NotificationOperationStatement;
import jolie.lang.parse.ast.NullProcessStatement;
import jolie.lang.parse.ast.OLSyntaxNode;
import jolie.lang.parse.ast.OneWayOperationDeclaration;
import jolie.lang.parse.ast.OneWayOperationStatement;
import jolie.lang.parse.ast.OrConditionNode;
import jolie.lang.parse.ast.OutputPortInfo;
import jolie.lang.parse.ast.ParallelStatement;
import jolie.lang.parse.ast.PointerStatement;
import jolie.lang.parse.ast.PostDecrementStatement;
import jolie.lang.parse.ast.PostIncrementStatement;
import jolie.lang.parse.ast.PreDecrementStatement;
import jolie.lang.parse.ast.PreIncrementStatement;
import jolie.lang.parse.ast.ProductExpressionNode;
import jolie.lang.parse.ast.Program;
import jolie.lang.parse.ast.RequestResponseOperationDeclaration;
import jolie.lang.parse.ast.RequestResponseOperationStatement;
import jolie.lang.parse.ast.RunStatement;
import jolie.lang.parse.ast.Scope;
import jolie.lang.parse.ast.SequenceStatement;
import jolie.lang.parse.ast.SolicitResponseOperationStatement;
import jolie.lang.parse.ast.SpawnStatement;
import jolie.lang.parse.ast.SubtractAssignStatement;
import jolie.lang.parse.ast.SumExpressionNode;
import jolie.lang.parse.ast.SynchronizedStatement;
import jolie.lang.parse.ast.ThrowStatement;
import jolie.lang.parse.ast.TypeCastExpressionNode;
import jolie.lang.parse.ast.UndefStatement;
import jolie.lang.parse.ast.ValueVectorSizeExpressionNode;
import jolie.lang.parse.ast.VariableExpressionNode;
import jolie.lang.parse.ast.VariablePathNode;
import jolie.lang.parse.ast.WhileStatement;
import jolie.lang.parse.ast.courier.CourierChoiceStatement;
import jolie.lang.parse.ast.courier.CourierDefinitionNode;
import jolie.lang.parse.ast.courier.NotificationForwardStatement;
import jolie.lang.parse.ast.courier.SolicitResponseForwardStatement;
import jolie.lang.parse.ast.types.TypeDefinitionLink;
import jolie.lang.parse.ast.types.TypeInlineDefinition;
import jolie.lang.parse.context.ParsingContext;
import jolie.util.Pair;

/**
 *
 * @author Fabrizio Montesi
 */
public class TypeChecker implements OLVisitor
{
	private class FlaggedVariablePathNode extends VariablePathNode
	{
		private final boolean isFresh;
		public FlaggedVariablePathNode( VariablePathNode path, boolean isFresh )
		{
			super( path.context(), path.type() );
			this.path().addAll( path.path() );
			this.isFresh = isFresh;
		}

		public boolean isFresh()
		{
			return isFresh;
		}
	}

	private class TypingResult
	{
		private final VariablePathSet< VariablePathNode > neededCorrPaths;
		private final VariablePathSet< FlaggedVariablePathNode > providedCorrPaths;
		private final VariablePathSet< VariablePathNode > neededVarPaths;
		private final VariablePathSet< VariablePathNode > providedVarPaths;
		private final Set< String > sessionOperations;
		private String startingOperation = null;

		private final VariablePathSet< VariablePathNode > invalidatedVarPaths;

		public TypingResult()
		{
			neededCorrPaths = new VariablePathSet();
			providedCorrPaths = new VariablePathSet();
			neededVarPaths = new VariablePathSet();
			providedVarPaths = new VariablePathSet();
			invalidatedVarPaths = new VariablePathSet();
			sessionOperations = new HashSet< String >();
		}

		public void registerOperationInput( String operation, boolean isStartingOperation )
		{
			if ( isStartingOperation ) {
				startingOperation = operation;
			} else {
				sessionOperations.add( operation );
			}
		}

		public void registerOperations( TypingResult other )
		{
			if ( this.startingOperation == null ) {
				this.startingOperation = other.startingOperation;
			}

			sessionOperations.addAll( other.sessionOperations );
		}

		public void provide( VariablePathNode path, boolean isFresh )
		{
			if ( path.isCSet() ) {
				providedCorrPaths.add( new FlaggedVariablePathNode( path, isFresh ) );
			} else {
				providedVarPaths.add( path );
			}
		}

		public void provide( FlaggedVariablePathNode path )
		{
			if ( path.isCSet() ) {
				providedCorrPaths.add( path );
			} else {
				providedVarPaths.add( path );
			}
		}

		public void provide( VariablePathNode path )
		{
			if ( path instanceof FlaggedVariablePathNode ) {
				provide( (FlaggedVariablePathNode) path );
			} else {
				provide( path, false );
			}
		}

		public void need( VariablePathNode path )
		{
			if ( path.isCSet() ) {
				neededCorrPaths.add( path );
			} else {
				neededVarPaths.add( path );
			}
		}

		public void needAll( TypingResult other )
		{
			for( VariablePathNode path : other.neededCorrPaths ) {
				need( path );
			}
			for( VariablePathNode path : other.neededVarPaths ) {
				need( path );
			}
		}

		public void provideAll( TypingResult other )
		{
			for( VariablePathNode path : other.providedCorrPaths ) {
				provide( path );
			}
			for( VariablePathNode path : other.providedVarPaths ) {
				provide( path );
			}
		}

		public void provideAll( VariablePathSet< ? extends VariablePathNode > other )
		{
			for( VariablePathNode path : other ) {
				provide( path );
			}
		}

		public void needAll( VariablePathSet< ? extends VariablePathNode > other )
		{
			for( VariablePathNode path : other ) {
				need( path );
			}
		}

		public void invalidateAll( TypingResult other )
		{
			for( VariablePathNode path : other.invalidatedVarPaths ) {
				invalidate( path );
			}
		}

		public void invalidate( VariablePathNode path )
		{
			invalidatedVarPaths.add( path );
			providedVarPaths.remove( path );
		}

		public void removeUnsharedProvided( TypingResult other )
		{
			List< VariablePathNode > toBeRemoved = new LinkedList< VariablePathNode >();
			for( VariablePathNode path : providedVarPaths ) {
				if ( !other.providedVarPaths.contains( path ) ) {
					toBeRemoved.add( path );
				}
			}
			for( VariablePathNode path : toBeRemoved ) {
				providedVarPaths.remove( path );
			}
		}
	}

	private final Program program;
	private boolean insideInit = false;
	private final CorrelationFunctionInfo correlationFunctionInfo;
	private final ExecutionMode executionMode;

	private TypingResult typingResult;
	private TypingResult entryTyping;
	private static final Logger logger = Logger.getLogger( "JOLIE" );
	private boolean valid = true;
	private final Map< String, TypingResult > definitionTyping = new HashMap< String, TypingResult >();
	private boolean sessionStarter = false;

	public TypeChecker( Program program, ExecutionMode executionMode, CorrelationFunctionInfo correlationFunctionInfo )
	{
		this.program = program;
		this.executionMode = executionMode;
		this.correlationFunctionInfo = correlationFunctionInfo;
	}

	private void error( OLSyntaxNode node, String message )
	{
		valid = false;
		if ( node != null ) {
			ParsingContext context = node.context();
			logger.severe( context.sourceName() + ":" + context.line() + ": " + message );
		} else {
			logger.severe( message );
		}
	}

	private boolean isDefinedBefore( VariablePathNode path )
	{
		if ( entryTyping.providedVarPaths.contains( path ) || entryTyping.providedCorrPaths.contains( path ) ) {
			return true;
		}
		return false;
	}

	public boolean check()
	{
		check( program, new TypingResult() );
		typingResult = definitionTyping.get( "main" );
		if ( typingResult == null ) {
			error( program, "Can not find the main entry point" );
		} else {
			checkMainTyping();
		}
		return valid;
	}

	private void checkMainTyping()
	{
		TypingResult initTyping = definitionTyping.get( "init" );
		if ( initTyping != null ) {
			addInitTypingToMain();
		}

		for( VariablePathNode path : typingResult.neededCorrPaths ) {
			error( path, "Correlation path " + path.toPrettyString() + " is not initialised before usage." );
		}

		for( VariablePathNode path : typingResult.neededVarPaths ) {
			error( path, "Variable " + path.toPrettyString() + " is not initialised before using it to initialise a correlation variable." );
		}

		VariablePathNode path;
		boolean isCorrelationSetFresh;
		for( CorrelationSetInfo cset : correlationFunctionInfo.correlationSets() ) {
			isCorrelationSetFresh = false;
			for( CorrelationVariableInfo cvar : cset.variables() ) {
				path = new VariablePathNode( cvar.correlationVariablePath().context(), VariablePathNode.Type.CSET );
				path.path().add( new Pair< OLSyntaxNode, OLSyntaxNode >(
					new ConstantStringExpression( cset.context(), Constants.CSETS ),
					new ConstantIntegerExpression( cset.context(), 0 )
				) );
				path.path().addAll( cvar.correlationVariablePath().path() );
				FlaggedVariablePathNode flaggedPath = typingResult.providedCorrPaths.getContained( path );
				if ( flaggedPath == null ) { // The two cases could be merged in a single if-then-else condition, but they are logically different.
					isCorrelationSetFresh = true; // We can set this because the correlation set is not used at all.
					break;
				} else if ( flaggedPath.isFresh() ) {
					isCorrelationSetFresh = true;
					break;
				}
			}
			if ( !isCorrelationSetFresh ) {
				error( cset, "Every correlation set must have at least one fresh value (maybe you are not using new?)." );
			}
		}
	}

	private void addInitTypingToMain()
	{
		TypingResult right = typingResult;
		typingResult = definitionTyping.get( "init" );
		for( VariablePathNode path : right.providedCorrPaths ) {
			if ( typingResult.providedCorrPaths.contains( path ) ) {
				error( path, "Correlation variables can not be defined more than one time." );
			} else {
				typingResult.provide( path );
			}
		}

		for( VariablePathNode path : right.providedVarPaths ) {
			typingResult.provide( path );
			typingResult.invalidatedVarPaths.remove( path );
		}

		for( VariablePathNode path : right.neededVarPaths ) {
			if ( !typingResult.providedVarPaths.contains( path ) ) {
				typingResult.need( path );
			}
		}

		for( VariablePathNode path : right.neededCorrPaths ) {
			if ( !typingResult.providedCorrPaths.contains( path ) ) {
				typingResult.need( path );
			}
		}

		typingResult.invalidateAll( right );
	}

	private TypingResult check( OLSyntaxNode n, TypingResult entryTyping )
	{
		this.entryTyping = entryTyping;
		TypingResult backup = typingResult;
		typingResult = new TypingResult();
		n.accept( this );
		TypingResult ret = typingResult;
		typingResult = backup;
		return ret;
	}

	public void visit( Program n )
	{
		for( OLSyntaxNode node : n.children() ) {
			check( node, new TypingResult() );
		}
	}

	public void visit( OneWayOperationDeclaration decl )
	{}

	public void visit( RequestResponseOperationDeclaration decl )
	{}

	public void visit( DefinitionNode n )
	{
		insideInit = false;
		TypingResult entry = null;
		if ( n.id().equals( "main" ) ) {
			sessionStarter = true;
			entry = definitionTyping.get( "init" );
		} else if ( n.id().equals( "init" ) ) {
			insideInit = true;
		}
		if ( entry == null ) {
			entry = new TypingResult();
		}
		definitionTyping.put( n.id(), check( n.body(), entry ) );
		
		if ( n.id().equals( "init" ) ) {
			for( VariablePathNode path : typingResult.providedCorrPaths ) {
				error( path, "Correlation variables can not be initialised in the init procedure." );
			}
		}
	}

	public void visit( ParallelStatement n )
	{
		if ( n.children().isEmpty() ) {
			return;
		}
		TypingResult entry = entryTyping;
		typingResult = check( n.children().get( 0 ), entry );
		TypingResult right;
		for( int i = 1; i < n.children().size(); i++ ) {
			right = check( n.children().get( i ), entry );
			for( VariablePathNode path : right.providedCorrPaths ) {
				if ( typingResult.providedCorrPaths.contains( path ) ) {
					error( path, "Correlation variables can not be defined more than one time." );
				} else {
					typingResult.provide( path );
				}
			}
			typingResult.provideAll( right.providedVarPaths );
			typingResult.needAll( right );
			typingResult.invalidateAll( right );
			typingResult.registerOperations( right );
		}
	}

	public void visit( SequenceStatement n )
	{
		if ( n.children().isEmpty() ) {
			return;
		}

		typingResult.provideAll( entryTyping );
		typingResult = check( n.children().get( 0 ), typingResult );
		TypingResult right;
		for( int i = 1; i < n.children().size(); i++ ) {
			right = check( n.children().get( i ), typingResult );
			for( VariablePathNode path : right.providedCorrPaths ) {
				if ( typingResult.providedCorrPaths.contains( path ) ) {
					error( path, "Correlation variables can not be defined more than one time." );
				} else {
					typingResult.provide( path );
				}
			}

			for( VariablePathNode path : right.providedVarPaths ) {
				typingResult.provide( path );
				typingResult.invalidatedVarPaths.remove( path );
			}

			for( VariablePathNode path : right.neededVarPaths ) {
				if ( !typingResult.providedVarPaths.contains( path ) ) {
					typingResult.need( path );
				}
			}

			for( VariablePathNode path : right.neededCorrPaths ) {
				if ( !typingResult.providedCorrPaths.contains( path ) ) {
					typingResult.need( path );
				}
			}

			typingResult.invalidateAll( right );
			typingResult.registerOperations( right );
		}
	}

	public void visit( NDChoiceStatement n )
	{
		if ( n.children().isEmpty() ) {
			return;
		}

		List< TypingResult > branchTypings = new LinkedList< TypingResult >();

		boolean origSessionStarter = sessionStarter;

		TypingResult entry = entryTyping;
		SequenceStatement seq;
		seq = new SequenceStatement( n.context() );
		seq.addChild( n.children().get( 0 ).key() );
		seq.addChild( n.children().get( 0 ).value() );
		typingResult = check( seq, entry );
		branchTypings.add( typingResult );

		TypingResult right;
		for( int i = 1; i < n.children().size(); i++ ) {
			sessionStarter = origSessionStarter;
			seq = new SequenceStatement( n.context() );
			seq.addChild( n.children().get( i ).key() );
			seq.addChild( n.children().get( i ).value() );
			right = check( seq, entry );
			branchTypings.add( right );
			typingResult.needAll( right );
			typingResult.invalidateAll( right );
			if ( !origSessionStarter ) {
				for( VariablePathNode path : typingResult.providedCorrPaths ) {
					if ( !right.providedCorrPaths.contains( path ) ) {
						error( path, "Correlation variables must be initialized in every branch." );
					}
				}
				for( VariablePathNode path : right.providedCorrPaths ) {
					if ( !typingResult.providedCorrPaths.contains( path ) ) {
						error( path, "Correlation variables must be initialized in every branch." );
					}
				}
				typingResult.registerOperations( right );
			}
			typingResult.removeUnsharedProvided( right );
			sessionStarter = false;
		}

		if ( origSessionStarter ) {
			for( TypingResult top : branchTypings ) {
				for( TypingResult branch : branchTypings ) {
					if ( top != branch && branch.sessionOperations.contains( top.startingOperation ) ) {
						error( program, "Operation " + top.startingOperation + " can not be used both as a starter and in the body of another session branch." );
					}
				}
			}
		}

		sessionStarter = false;
	}

	public void visit( OneWayOperationStatement n )
	{
		if ( executionMode == ExecutionMode.SINGLE ) {
			return;
		}

		typingResult.registerOperationInput( n.id(), sessionStarter );

		if ( n.inputVarPath() != null && n.inputVarPath().isCSet() ) {
			error( n, "Input operations can not receive on a correlation variable" );
		}

		CorrelationSetInfo cset = correlationFunctionInfo.operationCorrelationSetMap().get( n.id() );
		if ( !sessionStarter && !insideInit ) {
			if ( cset == null || cset.variables().isEmpty() ) {
				error( n, "No correlation set defined for operation " + n.id() );
			}
		}
		if ( cset != null ) {
			for( CorrelationSetInfo.CorrelationVariableInfo cvar : cset.variables() ) {
				VariablePathNode path = new VariablePathNode( cset.context(), VariablePathNode.Type.CSET );
				path.path().add( new Pair< OLSyntaxNode, OLSyntaxNode >(
					new ConstantStringExpression( cset.context(), Constants.CSETS ),
					new ConstantIntegerExpression( cset.context(), 0 )
				) );
				path.path().addAll( cvar.correlationVariablePath().path() );
				if ( sessionStarter ) {
					typingResult.provide( path, true );
				} else {
					typingResult.need( path );
				}
			}
		}
		
		sessionStarter = false;
	}

	public void visit( RequestResponseOperationStatement n )
	{
		if ( executionMode == ExecutionMode.SINGLE ) {
			return;
		}

		typingResult.registerOperationInput( n.id(), sessionStarter );

		if ( n.inputVarPath() != null && n.inputVarPath().isCSet() ) {
			error( n, "Input operations can not receive on a correlation variable" );
		}

		CorrelationSetInfo cset = correlationFunctionInfo.operationCorrelationSetMap().get( n.id() );
		if ( !sessionStarter && !insideInit ) {
			if ( cset == null || cset.variables().isEmpty() ) {
				error( n, "No correlation set defined for operation " + n.id() );
			}
		}

		if ( cset != null ) {
			for( CorrelationSetInfo.CorrelationVariableInfo cvar : cset.variables() ) {
				VariablePathNode path = new VariablePathNode( cset.context(), VariablePathNode.Type.CSET );
				path.path().add( new Pair< OLSyntaxNode, OLSyntaxNode >(
					new ConstantStringExpression( cset.context(), Constants.CSETS ),
					new ConstantIntegerExpression( cset.context(), 0 )
				) );
				path.path().addAll( cvar.correlationVariablePath().path() );
				if ( sessionStarter ) {
					typingResult.provide( path, true );
				} else {
					typingResult.need( path );
				}
			}
		}

		sessionStarter = false;

		TypingResult internalProcessTyping = check( n.process(), entryTyping );
		typingResult.needAll( internalProcessTyping );
		typingResult.provideAll( internalProcessTyping );
		typingResult.registerOperations( internalProcessTyping );
	}

	public void visit( NotificationOperationStatement n )
	{}

	public void visit( SolicitResponseOperationStatement n )
	{
		if ( n.inputVarPath() != null && n.inputVarPath().isCSet() ) {
			error( n, "Solicit-response statements can not receive on a correlation variable" );
		}
	}
	
	public void visit( FreshValueExpressionNode n )
	{}

	public void visit( LinkInStatement n )
	{}

	public void visit( LinkOutStatement n )
	{}

	public void visit( AssignStatement n )
	{
		if ( n.variablePath().isStatic() ) {
			if ( n.expression() instanceof ConstantIntegerExpression ) {
				typingResult.provide( n.variablePath() );
			} else if ( n.expression() instanceof ConstantRealExpression ) {
				typingResult.provide( n.variablePath() );
			} else if ( n.expression() instanceof ConstantStringExpression ) {
				typingResult.provide( n.variablePath() );
			} else if ( n.expression() instanceof PostDecrementStatement ) {
				typingResult.provide( n.variablePath() );
			} else if ( n.expression() instanceof PostIncrementStatement ) {
				typingResult.provide( n.variablePath() );
			} else if ( n.expression() instanceof PreDecrementStatement ) {
				typingResult.provide( n.variablePath() );
			} else if ( n.expression() instanceof PreIncrementStatement ) {
				typingResult.provide( n.variablePath() );
			} else if ( n.expression() instanceof VariableExpressionNode ) {
				VariablePathNode rightPath = ((VariableExpressionNode)n.expression()).variablePath();
				if ( rightPath.isStatic() && isDefinedBefore( rightPath ) ) {
					typingResult.provide( n.variablePath() );
				} else if ( n.variablePath().isCSet() ) {
					error( n, "Variable " + rightPath.toPrettyString() + " may be undefined before being used for defining correlation variable " + n.variablePath().toPrettyString() );
				}
			} else if ( n.expression() instanceof FreshValueExpressionNode ) {
				typingResult.provide( n.variablePath(), true );
			} else if ( n.variablePath().isCSet() ) {
				error( n, "Correlation variables must either be initialised with createSecureToken@SecurityUtils, a variable or a constant." );
			}
		}
	}

	public void visit( AddAssignStatement n )
	{}

	public void visit( SubtractAssignStatement n )
	{}

	public void visit( MultiplyAssignStatement n )
	{}

	public void visit( DivideAssignStatement n )
	{}

	public void visit( IfStatement n )
	{
		if ( n.children().isEmpty() ) {
			return;
		}

		TypingResult entry = entryTyping;
		typingResult = check( n.children().get( 0 ).value(), entry );
		TypingResult right;
		for( int i = 1; i < n.children().size(); i++ ) {
			right = check( n.children().get( i ).value(), entry );
			typingResult.needAll( right );
			typingResult.registerOperations( right );
			typingResult.invalidateAll( right );
			for( VariablePathNode path : typingResult.providedCorrPaths ) {
				if ( !right.providedCorrPaths.contains( path ) ) {
					error( path, "Correlation variables must be initialized in every if-then-else branch." );
				}
			}
			for( VariablePathNode path : right.providedCorrPaths ) {
				if ( !typingResult.providedCorrPaths.contains( path ) ) {
					error( path, "Correlation variables must be initialized in every if-then-else branch." );
				}
			}
			typingResult.removeUnsharedProvided( right );
		}

		if ( n.elseProcess() != null ) {
			right = check( n.elseProcess(), entry );
			typingResult.needAll( right );
			typingResult.registerOperations( right );
			typingResult.invalidateAll( right );
			for( VariablePathNode path : typingResult.providedCorrPaths ) {
				if ( !right.providedCorrPaths.contains( path ) ) {
					error( path, "Correlation variables must be initialized in every if-then-else branch." );
				}
			}
			for( VariablePathNode path : right.providedCorrPaths ) {
				if ( !typingResult.providedCorrPaths.contains( path ) ) {
					error( path, "Correlation variables must be initialized in every if-then-else branch." );
				}
			}
			typingResult.removeUnsharedProvided( right );
		}
	}

	public void visit( DefinitionCallStatement n )
	{
		typingResult = definitionTyping.get( n.id() );
		if ( typingResult == null ) {
			typingResult = new TypingResult();
			error( n, "Can not find definition " + n.id() );
		}
	}

	public void visit( WhileStatement n )
	{
		typingResult = check( n.body(), entryTyping );
		if ( !typingResult.providedCorrPaths.isEmpty() ) {
			error( n, "Initialising correlation variables in while loops is forbidden." );
		}
		typingResult.providedVarPaths.clear();
	}

	public void visit( OrConditionNode n )
	{}

	public void visit( AndConditionNode n )
	{}

	public void visit( NotConditionNode n )
	{}

	public void visit( CompareConditionNode n )
	{}

	public void visit( ExpressionConditionNode n )
	{}

	public void visit( ConstantIntegerExpression n )
	{}

	public void visit( ConstantRealExpression n )
	{}

	public void visit( ConstantStringExpression n )
	{}

	public void visit( ProductExpressionNode n )
	{}

	public void visit( SumExpressionNode n )
	{}

	public void visit( VariableExpressionNode n )
	{}

	public void visit( NullProcessStatement n )
	{}

	public void visit( Scope n )
	{
		typingResult = check( n.body(), entryTyping );
	}

	public void visit( InstallStatement n )
	{ // TODO check code inside install

	}

	public void visit( CompensateStatement n )
	{}

	public void visit( ThrowStatement n )
	{}

	public void visit( ExitStatement n )
	{}

	public void visit( ExecutionInfo n )
	{}

	public void visit( CorrelationSetInfo n )
	{}

	public void visit( InputPortInfo n )
	{}

	public void visit( OutputPortInfo n )
	{}

	public void visit( PointerStatement n )
	{
		typingResult.invalidate( n.rightPath() );
		typingResult.invalidate( n.leftPath() );
	}

	public void visit( DeepCopyStatement n )
	{
		typingResult.invalidate( n.rightPath() );
		typingResult.invalidate( n.leftPath() );
	}

	public void visit( RunStatement n )
	{}

	public void visit( UndefStatement n )
	{
		typingResult.invalidate( n.variablePath() );
	}

	public void visit( ValueVectorSizeExpressionNode n )
	{}

	public void visit( PreIncrementStatement n )
	{}

	public void visit( PostIncrementStatement n )
	{}

	public void visit( PreDecrementStatement n )
	{}

	public void visit( PostDecrementStatement n )
	{}

	public void visit( ForStatement n )
	{
		typingResult = check( n.body(), entryTyping );
		if ( !typingResult.providedCorrPaths.isEmpty() ) {
			error( n, "Initialising correlation variables in while loops is forbidden." );
		}
		typingResult.providedVarPaths.clear();
	}

	public void visit( ForEachStatement n )
	{
		typingResult = check( n.body(), entryTyping );
		if ( !typingResult.providedCorrPaths.isEmpty() ) {
			error( n, "Initialising correlation variables in while loops is forbidden." );
		}
		typingResult.providedVarPaths.clear();
	}

	public void visit( SpawnStatement n )
	{}

	public void visit( IsTypeExpressionNode n )
	{}

	public void visit( TypeCastExpressionNode n )
	{}

	public void visit( SynchronizedStatement n )
	{
		typingResult = check( n.body(), entryTyping );
	}

	public void visit( CurrentHandlerStatement n )
	{}

	public void visit( EmbeddedServiceNode n )
	{}

	public void visit( InstallFixedVariableExpressionNode n )
	{}

	public void visit( VariablePathNode n )
	{}

	public void visit( TypeInlineDefinition n )
	{}

	public void visit( TypeDefinitionLink n )
	{}

	public void visit( InterfaceDefinition n )
	{}

	public void visit( DocumentationComment n )
	{}
	
	public void visit( InterfaceExtenderDefinition n ) {}
	public void visit( CourierDefinitionNode n ) {}
	public void visit( CourierChoiceStatement n ) {}
	public void visit( NotificationForwardStatement n ) {}
	public void visit( SolicitResponseForwardStatement n ) {}
}
